package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.AllocationUseCase;
import com.example.msinventory.application.port.in.InventoryUnitUseCase;
import com.example.msinventory.application.port.in.PurchaseEntryUseCase;
import com.example.msinventory.application.port.in.PurchaseEntryUseCase.CreatePurchaseEntryCommand;
import com.example.msinventory.application.port.in.PurchaseEntryUseCase.PurchaseEntryResult;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.domain.exception.IdempotencyKeyReusedException;
import com.example.msinventory.domain.exception.NotEnoughPurchaseUnitsException;
import com.example.msinventory.domain.exception.PurchaseAlreadyRegisteredException;
import com.example.msinventory.domain.exception.PurchaseNotFoundException;
import com.example.msinventory.domain.model.InventoryMovement;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.MovementType;
import com.example.msinventory.domain.model.Warehouse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class PurchaseEntryServiceTest extends com.example.msinventory.ProductsStubbedTest {

    private static final Long PURCHASE = 500L;

    @Autowired PurchaseEntryUseCase service;
    @Autowired InventoryUnitUseCase unitService;
    @Autowired AllocationUseCase allocationService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ActorProvider actorProvider;
    @MockitoSpyBean com.example.msinventory.application.port.out.InventoryUnitRepositoryPort unitPort;

    private int keySeq;

    @BeforeEach
    void setUp() {
        when(actorProvider.currentActor()).thenReturn("op-1");
    }

    @AfterEach
    void cleanup() {
        org.mockito.Mockito.reset(unitPort);
        jdbc.update("delete from purchase_operations");
        jdbc.update("delete from inventory_movements");
        jdbc.update("delete from inventory_units");
        jdbc.update("delete from inventory_entries");
        jdbc.update("update item_conditions set active = true");
    }

    private CreatePurchaseEntryCommand cmd(int quantity, List<Long> reservations) {
        return new CreatePurchaseEntryCommand(PURCHASE, 3L, "SKU-1", "NEW", quantity, new BigDecimal("12.50"), null,
                LocalDate.now().plusDays(10), reservations);
    }

    private PurchaseEntryResult create(int quantity, List<Long> reservations) {
        return service.create("create-" + (keySeq++), "h", cmd(quantity, reservations));
    }

    private List<MovementType> types(Long unitId) {
        return unitService.movements(unitId).stream().map(InventoryMovement::type).toList();
    }

    private long total(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    @Test
    void purchaseEntry_doesNotCallProducts() {
        create(1, List.of());

        org.mockito.Mockito.verifyNoInteractions(products);
    }

    @Test
    void createPurchaseEntry_unitsInTransitUsa_reservedInOrder_invoiceNull() {
        PurchaseEntryResult r = create(3, List.of(11L, 12L));

        assertThat(r.replayed()).isFalse();
        assertThat(r.entry().getPurchaseId()).isEqualTo(PURCHASE);
        assertThat(r.entry().getSupplierId()).isEqualTo(3L);
        List<InventoryUnit> us = r.units();
        assertThat(us).hasSize(3).allSatisfy(u -> {
            assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.IN_TRANSIT);
            assertThat(u.getWarehouse()).isEqualTo(Warehouse.USA);
            assertThat(u.getInvoiceNumber()).isNull();
            assertThat(u.getSupplierPurchaseId()).isNull();
            assertThat(u.getPurchaseId()).isEqualTo(PURCHASE);
            assertThat(u.getSupplierId()).isEqualTo(3L);
            assertThat(u.getCostCurrency()).isEqualTo("USD");
        });
        assertThat(us).extracting(InventoryUnit::getAllocatedOrderItemId).containsExactly(11L, 12L, null);
        List<InventoryMovement> ms = unitService.movements(us.get(0).getId());
        assertThat(ms).hasSize(1);
        assertThat(ms.get(0).type()).isEqualTo(MovementType.ENTRY_SUPPLIER_PURCHASE);
        assertThat(ms.get(0).reference()).isEqualTo("purchase:500");
        assertThat(ms.get(0).toWarehouse()).isEqualTo(Warehouse.USA);
        assertThat(unitService.findOne(us.get(2).getId()).getPurchaseId()).isEqualTo(PURCHASE);
    }

    @Test
    void createPurchaseEntry_sameKeyReplay_otherBody422() {
        PurchaseEntryResult first = service.create("k1", "hash-a", cmd(2, List.of(11L)));
        PurchaseEntryResult replay = service.create("k1", "hash-a", cmd(2, List.of(11L)));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.units()).extracting(InventoryUnit::getId)
                .containsExactlyElementsOf(first.units().stream().map(InventoryUnit::getId).toList());
        assertThat(total("inventory_units")).isEqualTo(2);
        assertThatThrownBy(() -> service.create("k1", "hash-b", cmd(2, List.of(11L))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void createPurchaseEntry_samePurchaseOtherKey_conflict() {
        create(1, List.of());
        assertThatThrownBy(() -> create(1, List.of())).isInstanceOf(PurchaseAlreadyRegisteredException.class);
        assertThat(total("inventory_units")).isEqualTo(1);
    }

    @Test
    void createPurchaseEntry_moreReservationsThanQuantity_illegalArgument() {
        assertThatThrownBy(() -> create(1, List.of(11L, 12L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(2, List.of(11L, 11L))).isInstanceOf(IllegalArgumentException.class);
        assertThat(total("inventory_entries")).isZero();
    }

    @Test
    void createPurchaseEntry_inactiveConditionAccepted() {
        jdbc.update("update item_conditions set active = false where code = 'NEW'");
        PurchaseEntryResult r = create(1, List.of());
        assertThat(r.units()).hasSize(1);
    }

    @Test
    void arrivals_reservedFirstThenFree_movements() {
        List<InventoryUnit> us = create(4, List.of(11L, 12L)).units();
        // el item 12 no se pide: su unidad reservada se salta; llegan la reservada al 11 y 2 libres
        List<Long> ids = service.registerArrivals("arr-1", "h", PURCHASE, 3, List.of(11L));

        assertThat(ids).containsExactly(us.get(0).getId(), us.get(2).getId(), us.get(3).getId());
        InventoryUnit reserved = unitService.findOne(us.get(0).getId());
        assertThat(reserved.getStatus()).isEqualTo(InventoryUnitStatus.ALLOCATED);
        assertThat(reserved.getAllocatedOrderItemId()).isEqualTo(11L);
        assertThat(types(reserved.getId())).containsExactly(MovementType.ENTRY_SUPPLIER_PURCHASE,
                MovementType.ARRIVAL, MovementType.EXIT_ORDER_ALLOCATION);
        assertThat(unitService.movements(reserved.getId()).get(2).reference()).isEqualTo("order-item:11");
        assertThat(unitService.findOne(us.get(2).getId()).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(types(us.get(2).getId())).containsExactly(MovementType.ENTRY_SUPPLIER_PURCHASE,
                MovementType.ARRIVAL);
        assertThat(unitService.findOne(us.get(1).getId()).getStatus()).isEqualTo(InventoryUnitStatus.IN_TRANSIT);
    }

    @Test
    void arrivals_reservationReleasedInBetween_fillsWithFree() {
        List<InventoryUnit> us = create(2, List.of(11L)).units();
        allocationService.release(us.get(0).getId(), 11L);

        List<Long> ids = service.registerArrivals("arr-1", "h", PURCHASE, 1, List.of(11L));

        assertThat(ids).containsExactly(us.get(0).getId());
        assertThat(unitService.findOne(us.get(0).getId()).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(types(us.get(0).getId())).containsExactly(MovementType.ENTRY_SUPPLIER_PURCHASE,
                MovementType.RESERVATION_RELEASED, MovementType.ARRIVAL);
    }

    @Test
    void arrivals_notEnough_409_nothingChanged() {
        List<InventoryUnit> us = create(2, List.of(11L)).units();

        assertThatThrownBy(() -> service.registerArrivals("arr-1", "h", PURCHASE, 3, List.of(11L)))
                .isInstanceOf(NotEnoughPurchaseUnitsException.class);

        assertThat(total("purchase_operations")).isZero();
        assertThat(total("inventory_movements")).isEqualTo(2);
        us.forEach(u -> assertThat(unitService.findOne(u.getId()).getStatus())
                .isEqualTo(InventoryUnitStatus.IN_TRANSIT));
    }

    @Test
    void arrivals_unknownPurchase_notFound() {
        assertThatThrownBy(() -> service.registerArrivals("arr-1", "h", 9999L, 1, List.of()))
                .isInstanceOf(PurchaseNotFoundException.class);
    }

    @Test
    void arrivals_sameKey_replayNoNewMovements() {
        create(2, List.of(11L));
        List<Long> first = service.registerArrivals("arr-1", "h", PURCHASE, 1, List.of(11L));
        long movements = total("inventory_movements");

        List<Long> replay = service.registerArrivals("arr-1", "h", PURCHASE, 1, List.of(11L));

        assertThat(replay).isEqualTo(first);
        assertThat(total("inventory_movements")).isEqualTo(movements);
        assertThatThrownBy(() -> service.registerArrivals("arr-1", "other", PURCHASE, 1, List.of(11L)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThatThrownBy(() -> service.markNotReceived("arr-1", "h", PURCHASE, 1, List.of(11L), "x"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void notReceived_exitMovementWithReason_clearsReservation() {
        List<InventoryUnit> us = create(2, List.of(11L)).units();

        List<Long> ids = service.markNotReceived("nr-1", "h", PURCHASE, 2, List.of(11L), "no llego");

        assertThat(ids).containsExactly(us.get(0).getId(), us.get(1).getId());
        InventoryUnit u = unitService.findOne(us.get(0).getId());
        assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.NOT_RECEIVED);
        assertThat(u.getAllocatedOrderItemId()).isNull();
        List<InventoryMovement> ms = unitService.movements(u.getId());
        assertThat(ms.get(1).type()).isEqualTo(MovementType.EXIT_NOT_RECEIVED);
        assertThat(ms.get(1).reason()).isEqualTo("no llego");
        assertThat(ms.get(1).fromWarehouse()).isEqualTo(Warehouse.USA);
        assertThat(service.markNotReceived("nr-1", "h", PURCHASE, 2, List.of(11L), "no llego")).isEqualTo(ids);
        assertThatThrownBy(() -> service.markNotReceived("nr-2", "h", PURCHASE, 1, List.of(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void genericOpsOnInTransitPurchaseUnit_conflict() {
        Long id = create(1, List.of()).units().get(0).getId();

        assertThatThrownBy(() -> unitService.arrive(id))
                .isInstanceOf(com.example.msinventory.domain.exception.UsePurchaseFlowException.class);
        assertThatThrownBy(() -> unitService.arriveAll(List.of(id)))
                .isInstanceOf(com.example.msinventory.domain.exception.UsePurchaseFlowException.class);
        assertThatThrownBy(() -> unitService.writeOff(id, "x"))
                .isInstanceOf(com.example.msinventory.domain.exception.UsePurchaseFlowException.class);
        assertThat(unitService.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.IN_TRANSIT);
    }

    @Test
    void createReplay_returnsOriginalReservationPairs_notLiveState() {
        PurchaseEntryResult first = service.create("k1", "h", cmd(3, List.of(11L, 12L)));
        List<Long> ids = first.units().stream().map(InventoryUnit::getId).toList();
        allocationService.release(ids.get(0), 11L);
        service.markNotReceived("nr", "h", PURCHASE, 1, List.of(12L), "x");
        service.registerArrivals("arr", "h", PURCHASE, 2, List.of());
        allocationService.allocate(ids.get(2), 77L, "SKU-1");

        PurchaseEntryResult replay = service.create("k1", "h", cmd(3, List.of(11L, 12L)));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.reservations()).containsExactly(11L, 12L);
        assertThat(replay.units()).extracting(InventoryUnit::getId).containsExactlyElementsOf(ids);
        assertThat(com.example.msinventory.infrastructure.web.dto.PurchaseEntryResponse.from(replay).reservations())
                .extracting(r -> r.orderItemId() + ":" + r.unitId())
                .containsExactly("11:" + ids.get(0), "12:" + ids.get(1));
    }

    /**
     * Carrera: el pre-chequeo no ve la cobertura, pero el indice unico de allocated_order_item_id la rechaza al
     * insertar. Debe ser 409 ALLOCATION_CONFLICT (no un 500), sin dejar nada creado.
     */
    @Test
    void createPurchaseEntry_uniqueRaceOnReservation_allocationConflict() {
        service.create("other", "h", new CreatePurchaseEntryCommand(501L, 3L, "SKU-1", "NEW", 1,
                new BigDecimal("12.50"), null, LocalDate.now().plusDays(10), List.of(11L)));
        org.mockito.Mockito.doReturn(java.util.Optional.empty()).doCallRealMethod()
                .when(unitPort).findByAllocatedOrderItemId(11L);

        assertThatThrownBy(() -> service.create("mine", "h", cmd(2, List.of(11L))))
                .isInstanceOf(com.example.msinventory.domain.exception.AllocationConflictException.class)
                .hasMessageContaining("11");

        assertThat(total("inventory_units")).isEqualTo(1);
        assertThat(total("inventory_entries")).isEqualTo(1);
    }
}
