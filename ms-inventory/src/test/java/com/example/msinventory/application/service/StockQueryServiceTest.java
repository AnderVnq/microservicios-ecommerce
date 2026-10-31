package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.AllocationUseCase;
import com.example.msinventory.application.port.in.InventoryEntryUseCase;
import com.example.msinventory.application.port.in.InventoryUnitUseCase;
import com.example.msinventory.application.port.in.KardexLine;
import com.example.msinventory.application.port.in.KardexReport;
import com.example.msinventory.application.port.in.RegisterEntryCommand;
import com.example.msinventory.application.port.in.StockQueryUseCase;
import com.example.msinventory.application.port.in.StockRow;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.domain.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class StockQueryServiceTest extends com.example.msinventory.ProductsStubbedTest {

    @Autowired StockQueryUseCase service;
    @Autowired InventoryEntryUseCase entryService;
    @Autowired InventoryUnitUseCase unitService;
    @Autowired AllocationUseCase allocationService;
    @Autowired com.example.msinventory.application.port.in.PurchaseEntryUseCase purchaseService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ActorProvider actorProvider;

    private int keySeq;

    @BeforeEach
    void setUp() {
        when(actorProvider.currentActor()).thenReturn("op-1");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from purchase_operations");
        jdbc.update("delete from inventory_movements");
        jdbc.update("delete from inventory_units");
        jdbc.update("delete from inventory_entries");
    }

    private List<Long> register(String sku, String condition, Warehouse wh, int qty, String cost, String currency,
                                InventoryUnitStatus status) {
        LocalDate eta = status == InventoryUnitStatus.IN_TRANSIT ? LocalDate.now().plusDays(5) : null;
        RegisterEntryCommand cmd = new RegisterEntryCommand(EntryType.STOCK_PURCHASE, sku, condition, wh, qty,
                new BigDecimal(cost), currency, status, eta, null, null, null, null, null);
        return entryService.register("k-" + (keySeq++), "h", cmd).units().stream()
                .map(InventoryUnit::getId).toList();
    }

    private List<Long> available(String sku, Warehouse wh, int qty, String cost) {
        return register(sku, "NEW", wh, qty, cost, null, InventoryUnitStatus.AVAILABLE);
    }

    @Test
    void stock_groupsInTransitAndAvailable_excludesAllocatedAndExits() {
        List<Long> usaNew = available("SKU-1", Warehouse.USA, 4, "10.00");
        register("SKU-1", "NEW", Warehouse.USA, 2, "10.00", null, InventoryUnitStatus.IN_TRANSIT);
        register("SKU-1", "USED", Warehouse.LIMA, 1, "5.00", null, InventoryUnitStatus.AVAILABLE);
        available("SKU-2", Warehouse.USA, 1, "7.00");
        allocationService.allocate(usaNew.get(0), 100L, "SKU-1");
        unitService.writeOff(usaNew.get(1), "danado");
        unitService.supplierReturn(usaNew.get(2), null);

        List<StockRow> all = service.stock(null, null, null);

        assertThat(all).containsExactly(
                new StockRow("SKU-1", "NEW", Warehouse.USA, 2, 1, 3, 0),
                new StockRow("SKU-1", "USED", Warehouse.LIMA, 0, 1, 1, 0),
                new StockRow("SKU-2", "NEW", Warehouse.USA, 0, 1, 1, 0));
        assertThat(service.stock("SKU-1", Warehouse.USA, "NEW"))
                .containsExactly(new StockRow("SKU-1", "NEW", Warehouse.USA, 2, 1, 3, 0));
        assertThat(service.stock("SKU-1", Warehouse.LIMA, null))
                .containsExactly(new StockRow("SKU-1", "USED", Warehouse.LIMA, 0, 1, 1, 0));
    }

    @Test
    void kardex_runningBalanceAndValue() {
        List<Long> ids = available("SKU-1", Warehouse.USA, 2, "100.00");
        allocationService.allocate(ids.get(0), 100L, "SKU-1");

        KardexReport r = service.kardex("SKU-1", Warehouse.USA, null, null, null, null);

        assertThat(r.currency()).isEqualTo("USD");
        assertThat(r.openingQuantity()).isZero();
        assertThat(r.openingValue()).isEqualByComparingTo("0");
        assertThat(r.lines()).extracting("type").containsExactly(MovementType.ENTRY_STOCK_PURCHASE,
                MovementType.ENTRY_STOCK_PURCHASE, MovementType.EXIT_ORDER_ALLOCATION);
        assertThat(r.lines()).extracting("balanceQuantity").containsExactly(1L, 2L, 1L);
        assertThat(r.lines().get(1).balanceValue()).isEqualByComparingTo("200.00");
        assertThat(r.lines().get(1).balanceValue().scale()).isEqualTo(2);
        assertThat(r.lines().get(2).quantityOut()).isEqualTo(1);
        assertThat(r.lines().get(2).quantityIn()).isZero();
        assertThat(r.lines().get(2).balanceValue()).isEqualByComparingTo("100.00");
        assertThat(r.closingQuantity()).isEqualTo(1);
        assertThat(r.closingValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void kardex_transferIsOutOfOriginInToDestination() {
        Long id = available("SKU-1", Warehouse.USA, 1, "50.00").get(0);
        unitService.transfer(id, Warehouse.LIMA, LocalDate.now().plusDays(3));
        unitService.arrive(id);

        KardexReport usa = service.kardex("SKU-1", Warehouse.USA, null, null, null, null);
        KardexReport lima = service.kardex("SKU-1", Warehouse.LIMA, null, null, null, null);
        KardexReport all = service.kardex("SKU-1", null, null, null, null, null);

        assertThat(usa.closingQuantity()).isZero();
        assertThat(usa.closingValue()).isEqualByComparingTo("0");
        assertThat(usa.lines()).extracting("type")
                .containsExactly(MovementType.ENTRY_STOCK_PURCHASE, MovementType.TRANSFER);
        assertThat(lima.closingQuantity()).isEqualTo(1);
        assertThat(lima.closingValue()).isEqualByComparingTo("50.00");
        assertThat(lima.lines()).extracting("type").containsExactly(MovementType.TRANSFER, MovementType.ARRIVAL);
        assertThat(all.closingQuantity()).isEqualTo(1);
        assertThat(all.closingValue()).isEqualByComparingTo("50.00");
        assertThat(all.lines()).hasSize(3);
    }

    @Test
    void kardex_openingBalanceBeforeFrom() {
        List<Long> ids = available("SKU-1", Warehouse.USA, 2, "100.00");
        jdbc.update("update inventory_movements set occurred_at = ? where inventory_unit_id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2026-01-15T10:00:00Z")), ids.get(0));
        jdbc.update("update inventory_movements set occurred_at = ? where inventory_unit_id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2026-02-01T00:00:00Z")), ids.get(1));

        KardexReport r = service.kardex("SKU-1", Warehouse.USA, null, null,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1));

        assertThat(r.openingQuantity()).isEqualTo(1);
        assertThat(r.openingValue()).isEqualByComparingTo("100.00");
        assertThat(r.lines()).hasSize(1);
        assertThat(r.lines().get(0).balanceQuantity()).isEqualTo(2);
        assertThat(r.closingQuantity()).isEqualTo(2);
        assertThat(r.closingValue()).isEqualByComparingTo("200.00");

        KardexReport before = service.kardex("SKU-1", Warehouse.USA, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(before.lines()).hasSize(1);
        assertThat(before.closingQuantity()).isEqualTo(1);

        KardexReport after = service.kardex("SKU-1", Warehouse.USA, null, null, LocalDate.of(2026, 3, 1), null);
        assertThat(after.openingQuantity()).isEqualTo(2);
        assertThat(after.lines()).isEmpty();
        assertThat(after.closingQuantity()).isEqualTo(2);
    }

    @Test
    void kardex_otherCurrencyExcluded() {
        available("SKU-1", Warehouse.USA, 1, "100.00");
        register("SKU-1", "NEW", Warehouse.USA, 2, "300.00", "PEN", InventoryUnitStatus.AVAILABLE);

        KardexReport usd = service.kardex("SKU-1", Warehouse.USA, null, null, null, null);
        KardexReport pen = service.kardex("SKU-1", Warehouse.USA, null, "PEN", null, null);

        assertThat(usd.closingQuantity()).isEqualTo(1);
        assertThat(usd.closingValue()).isEqualByComparingTo("100.00");
        assertThat(pen.currency()).isEqualTo("PEN");
        assertThat(pen.closingQuantity()).isEqualTo(2);
        assertThat(pen.closingValue()).isEqualByComparingTo("600.00");
    }

    @Test
    void kardex_conditionFilter_andReleaseCountsAsEntry() {
        Long id = available("SKU-1", Warehouse.USA, 1, "10.00").get(0);
        register("SKU-1", "USED", Warehouse.USA, 1, "4.00", null, InventoryUnitStatus.AVAILABLE);
        allocationService.allocate(id, 7L, "SKU-1");
        allocationService.release(id, 7L);

        KardexReport newOnly = service.kardex("SKU-1", Warehouse.USA, "NEW", null, null, null);

        assertThat(newOnly.lines()).extracting("type").containsExactly(MovementType.ENTRY_STOCK_PURCHASE,
                MovementType.EXIT_ORDER_ALLOCATION, MovementType.ALLOCATION_RELEASED);
        assertThat(newOnly.closingQuantity()).isEqualTo(1);
        assertThat(newOnly.closingValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void kardex_blankSku_rejected() {
        assertThatThrownBy(() -> service.kardex(" ", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.kardex(null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<Long> purchase(int quantity, List<Long> reservations) {
        return purchaseService.create("p-" + (keySeq++), "h", new com.example.msinventory.application.port.in
                        .PurchaseEntryUseCase.CreatePurchaseEntryCommand(900L, 3L, "SKU-9", "NEW", quantity,
                        new BigDecimal("10.00"), null, LocalDate.now().plusDays(5), reservations))
                .units().stream().map(InventoryUnit::getId).toList();
    }

    @Test
    void stock_reservedInTransitCounted() {
        List<Long> ids = purchase(3, List.of(1L, 2L));

        assertThat(service.stock("SKU-9", null, null)).containsExactly(
                new StockRow("SKU-9", "NEW", Warehouse.USA, 3, 0, 3, 2));

        allocationService.release(ids.get(0), 1L);
        purchaseService.markNotReceived("nr", "h", 900L, 1, List.of(2L), "x");

        assertThat(service.stock("SKU-9", null, null)).containsExactly(
                new StockRow("SKU-9", "NEW", Warehouse.USA, 2, 0, 2, 0));
    }

    @Test
    void kardex_notReceivedIsOut() {
        purchase(2, List.of(1L));
        purchaseService.registerArrivals("arr", "h", 900L, 1, List.of(1L));
        purchaseService.markNotReceived("nr", "h", 900L, 1, List.of(), "perdida");

        KardexReport k = service.kardex("SKU-9", null, null, null, null, LocalDate.now(java.time.ZoneOffset.UTC));

        assertThat(k.lines()).extracting(KardexLine::type).containsExactly(
                MovementType.ENTRY_SUPPLIER_PURCHASE, MovementType.ENTRY_SUPPLIER_PURCHASE, MovementType.ARRIVAL,
                MovementType.EXIT_ORDER_ALLOCATION, MovementType.EXIT_NOT_RECEIVED);
        assertThat(k.lines().get(2).quantityIn()).isZero();
        assertThat(k.lines().get(2).quantityOut()).isZero();
        assertThat(k.lines().get(4).quantityOut()).isEqualTo(1);
        assertThat(k.closingQuantity()).isZero();
    }
}
