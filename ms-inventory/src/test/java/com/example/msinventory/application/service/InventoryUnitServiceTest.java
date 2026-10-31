package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.InventoryEntryUseCase;
import com.example.msinventory.application.port.in.InventoryUnitUseCase;
import com.example.msinventory.application.port.in.RegisterEntryCommand;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort.UnitFilter;
import com.example.msinventory.domain.exception.InvalidUnitTransitionException;
import com.example.msinventory.domain.exception.UnitNotFoundException;
import com.example.msinventory.domain.exception.UsePurchaseFlowException;
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
class InventoryUnitServiceTest extends com.example.msinventory.ProductsStubbedTest {

    @Autowired InventoryUnitUseCase service;
    @Autowired InventoryEntryUseCase entryService;
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

    private Long register(InventoryUnitStatus status, LocalDate eta, LocalDate deadline) {
        RegisterEntryCommand cmd = new RegisterEntryCommand(EntryType.STOCK_PURCHASE, "SKU-1", "NEW", Warehouse.USA, 1,
                new BigDecimal("12.50"), null, status, eta, deadline, null, null, null, null);
        return entryService.register("k-" + (keySeq++), "h", cmd).units().get(0).getId();
    }

    private Long inTransit() {
        return register(InventoryUnitStatus.IN_TRANSIT, LocalDate.now().plusDays(5), null);
    }

    private Long available() {
        return register(InventoryUnitStatus.AVAILABLE, null, null);
    }

    private List<MovementType> types(Long id) {
        return service.movements(id).stream().map(InventoryMovement::type).toList();
    }

    @Test
    void arrive_writesArrival_secondCallNoNewMovement() {
        Long id = inTransit();

        InventoryUnit first = service.arrive(id);
        InventoryUnit second = service.arrive(id);

        assertThat(first.getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(first.getArrivedAt()).isNotNull();
        assertThat(second.getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        List<InventoryMovement> ms = service.movements(id);
        assertThat(ms).hasSize(2);
        InventoryMovement arrival = ms.get(1);
        assertThat(arrival.type()).isEqualTo(MovementType.ARRIVAL);
        assertThat(arrival.toWarehouse()).isEqualTo(Warehouse.USA);
        assertThat(arrival.performedBy()).isEqualTo("op-1");
        assertThat(service.findOne(id).getArrivedAt()).isNotNull();
    }

    @Test
    void arrive_unknownId_notFound() {
        assertThatThrownBy(() -> service.arrive(999_999L)).isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    void arriveAll_arrivesEachUnit() {
        Long a = inTransit();
        Long b = inTransit();

        List<InventoryUnit> result = service.arriveAll(List.of(a, b));

        assertThat(result).extracting(InventoryUnit::getId).containsExactly(a, b);
        assertThat(result).allSatisfy(u -> assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE));
        assertThat(types(a)).containsExactly(MovementType.ENTRY_STOCK_PURCHASE, MovementType.ARRIVAL);
        assertThat(types(b)).containsExactly(MovementType.ENTRY_STOCK_PURCHASE, MovementType.ARRIVAL);
    }

    @Test
    void arriveAll_oneFails_othersStillArrive_thenThrows() {
        Long a = inTransit();
        Long b = inTransit();
        Long writtenOff = inTransit();
        service.writeOff(writtenOff, "perdida");

        assertThatThrownBy(() -> service.arriveAll(List.of(a, 999_999L, writtenOff, b)))
                .isInstanceOf(UnitNotFoundException.class);

        assertThat(service.findOne(a).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(service.findOne(b).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(service.findOne(writtenOff).getStatus()).isEqualTo(InventoryUnitStatus.WRITTEN_OFF);
    }

    @Test
    void transfer_thenArrive_endsAvailableInDestination() {
        Long id = available();
        LocalDate eta = LocalDate.now().plusDays(7);

        InventoryUnit moved = service.transfer(id, Warehouse.LIMA, eta);
        assertThat(moved.getStatus()).isEqualTo(InventoryUnitStatus.IN_TRANSIT);
        assertThat(moved.getWarehouse()).isEqualTo(Warehouse.LIMA);
        assertThat(moved.getExpectedArrivalDate()).isEqualTo(eta);

        InventoryUnit arrived = service.arrive(id);

        assertThat(arrived.getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(arrived.getWarehouse()).isEqualTo(Warehouse.LIMA);
        List<InventoryMovement> ms = service.movements(id);
        assertThat(ms).extracting(InventoryMovement::type)
                .containsExactly(MovementType.ENTRY_STOCK_PURCHASE, MovementType.TRANSFER, MovementType.ARRIVAL);
        assertThat(ms.get(1).fromWarehouse()).isEqualTo(Warehouse.USA);
        assertThat(ms.get(1).toWarehouse()).isEqualTo(Warehouse.LIMA);
        assertThat(ms.get(2).toWarehouse()).isEqualTo(Warehouse.LIMA);
    }

    @Test
    void transfer_toSameWarehouse_conflict_noMovement() {
        Long id = available();

        assertThatThrownBy(() -> service.transfer(id, Warehouse.USA, LocalDate.now().plusDays(1)))
                .isInstanceOf(InvalidUnitTransitionException.class);
        assertThat(types(id)).containsExactly(MovementType.ENTRY_STOCK_PURCHASE);
    }

    @Test
    void writeOff_twice_singleMovement() {
        Long id = available();

        service.writeOff(id, "danado");
        InventoryUnit again = service.writeOff(id, "danado");

        assertThat(again.getStatus()).isEqualTo(InventoryUnitStatus.WRITTEN_OFF);
        List<InventoryMovement> ms = service.movements(id);
        assertThat(ms).hasSize(2);
        InventoryMovement exit = ms.get(1);
        assertThat(exit.type()).isEqualTo(MovementType.EXIT_WRITE_OFF);
        assertThat(exit.fromWarehouse()).isEqualTo(Warehouse.USA);
        assertThat(exit.toWarehouse()).isNull();
        assertThat(exit.reason()).isEqualTo("danado");
        assertThat(exit.unitCost()).isEqualByComparingTo("12.50");
    }

    @Test
    void supplierReturn_afterWriteOff_conflict() {
        Long id = available();
        service.writeOff(id, "danado");

        assertThatThrownBy(() -> service.supplierReturn(id, null))
                .isInstanceOf(InvalidUnitTransitionException.class);
        assertThat(types(id)).hasSize(2);
        assertThat(service.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.WRITTEN_OFF);
    }

    @Test
    void supplierReturn_reasonOptional_writesMovement() {
        Long id = available();

        InventoryUnit u = service.supplierReturn(id, null);

        assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.RETURNED_TO_SUPPLIER);
        assertThat(types(id)).containsExactly(MovementType.ENTRY_STOCK_PURCHASE, MovementType.EXIT_SUPPLIER_RETURN);
    }

    @Test
    void adjustOut_writesAdjustmentMovement() {
        Long id = available();

        InventoryUnit u = service.adjustOut(id, "conteo");

        assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.ADJUSTED_OUT);
        InventoryMovement m = service.movements(id).get(1);
        assertThat(m.type()).isEqualTo(MovementType.EXIT_ADJUSTMENT);
        assertThat(m.reason()).isEqualTo("conteo");
    }

    @Test
    void writeOffAndAdjustOut_blankReason_rejected_noChange() {
        Long id = available();

        assertThatThrownBy(() -> service.writeOff(id, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.adjustOut(id, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(types(id)).hasSize(1);
    }

    @Test
    void writeOff_inTransit_allowed() {
        Long id = inTransit();

        InventoryUnit u = service.writeOff(id, "perdida en transito");

        assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.WRITTEN_OFF);
        assertThat(types(id)).containsExactly(MovementType.ENTRY_STOCK_PURCHASE, MovementType.EXIT_WRITE_OFF);
    }

    @Test
    void updateDates_onlyDatesChange() {
        Long id = inTransit();
        InventoryUnit before = service.findOne(id);
        LocalDate eta = LocalDate.now().plusDays(20);
        LocalDate deadline = LocalDate.now().plusDays(40);

        InventoryUnit after = service.updateDates(id, eta, deadline);

        assertThat(after.getExpectedArrivalDate()).isEqualTo(eta);
        assertThat(after.getSupplierReturnDeadline()).isEqualTo(deadline);
        assertThat(after.getStatus()).isEqualTo(before.getStatus());
        assertThat(after.getWarehouse()).isEqualTo(before.getWarehouse());
        assertThat(after.getUnitCost()).isEqualByComparingTo(before.getUnitCost());
        assertThat(after.getSku()).isEqualTo(before.getSku());
        assertThat(types(id)).hasSize(1);
        assertThat(service.findOne(id).getExpectedArrivalDate()).isEqualTo(eta);
    }

    @Test
    void updateDates_patch_omittedExpectedArrival_keepsIt() {
        Long id = inTransit();
        LocalDate original = service.findOne(id).getExpectedArrivalDate();
        LocalDate deadline = LocalDate.now().plusDays(3);

        InventoryUnit u = service.updateDates(id, null, deadline);

        assertThat(u.getExpectedArrivalDate()).isEqualTo(original);
        assertThat(u.getSupplierReturnDeadline()).isEqualTo(deadline);
        assertThat(service.findOne(id).getExpectedArrivalDate()).isEqualTo(original);
    }

    @Test
    void updateDates_patch_onlyExpectedArrival_keepsSupplierReturnDeadline() {
        LocalDate deadline = LocalDate.now().plusDays(30);
        Long id = register(InventoryUnitStatus.IN_TRANSIT, LocalDate.now().plusDays(5), deadline);
        LocalDate eta = LocalDate.now().plusDays(9);

        InventoryUnit u = service.updateDates(id, eta, null);

        assertThat(u.getExpectedArrivalDate()).isEqualTo(eta);
        assertThat(u.getSupplierReturnDeadline()).isEqualTo(deadline);
        assertThat(service.findOne(id).getSupplierReturnDeadline()).isEqualTo(deadline);
    }

    @Test
    void updateDates_patch_emptyBody_changesNothing() {
        LocalDate eta = LocalDate.now().plusDays(5);
        LocalDate deadline = LocalDate.now().plusDays(30);
        Long id = register(InventoryUnitStatus.IN_TRANSIT, eta, deadline);

        InventoryUnit u = service.updateDates(id, null, null);

        assertThat(u.getExpectedArrivalDate()).isEqualTo(eta);
        assertThat(u.getSupplierReturnDeadline()).isEqualTo(deadline);
        assertThat(types(id)).hasSize(1);
    }

    @Test
    void updateDates_available_allowsNullExpectedArrival() {
        Long id = available();

        InventoryUnit u = service.updateDates(id, null, LocalDate.now().plusDays(3));

        assertThat(u.getExpectedArrivalDate()).isNull();
    }

    @Test
    void unknownId_notFound_forAllOperations() {
        assertThatThrownBy(() -> service.findOne(999_999L)).isInstanceOf(UnitNotFoundException.class);
        assertThatThrownBy(() -> service.movements(999_999L)).isInstanceOf(UnitNotFoundException.class);
        assertThatThrownBy(() -> service.writeOff(999_999L, "x")).isInstanceOf(UnitNotFoundException.class);
        assertThatThrownBy(() -> service.updateDates(999_999L, null, null)).isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    void search_returnDeadlineBefore() {
        Long id = register(InventoryUnitStatus.AVAILABLE, null, LocalDate.of(2026, 11, 1));

        List<InventoryUnit> found = service.search(new UnitFilter(null, null, null, null, LocalDate.of(2026, 11, 2)));
        List<InventoryUnit> notFound = service.search(new UnitFilter(null, null, null, null, LocalDate.of(2026, 11, 1)));

        assertThat(found).extracting(InventoryUnit::getId).containsExactly(id);
        assertThat(notFound).isEmpty();
    }

    @Test
    void genericOpsOnInTransitPurchaseUnit_conflict() {
        Long id = purchaseService.create("pk", "h", new com.example.msinventory.application.port.in
                .PurchaseEntryUseCase.CreatePurchaseEntryCommand(901L, 3L, "SKU-1", "NEW", 1, new BigDecimal("5.00"),
                null, LocalDate.now().plusDays(5), List.of())).units().get(0).getId();

        assertThatThrownBy(() -> service.arrive(id)).isInstanceOf(UsePurchaseFlowException.class);
        assertThatThrownBy(() -> service.arriveAll(List.of(id))).isInstanceOf(UsePurchaseFlowException.class);
        assertThatThrownBy(() -> service.writeOff(id, "x")).isInstanceOf(UsePurchaseFlowException.class);
        assertThat(service.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.IN_TRANSIT);
    }
}
