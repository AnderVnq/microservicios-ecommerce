package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.AllocationUseCase;
import com.example.msinventory.application.port.in.InventoryEntryUseCase;
import com.example.msinventory.application.port.in.InventoryUnitUseCase;
import com.example.msinventory.application.port.in.RegisterEntryCommand;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.application.port.out.InventoryMovementRepositoryPort;
import com.example.msinventory.domain.exception.AllocationConflictException;
import com.example.msinventory.domain.exception.InvalidUnitTransitionException;
import com.example.msinventory.domain.exception.SkuMismatchException;
import com.example.msinventory.domain.exception.UnitNotFoundException;
import com.example.msinventory.domain.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class AllocationServiceTest extends com.example.msinventory.ProductsStubbedTest {

    @Autowired AllocationUseCase service;
    @Autowired InventoryEntryUseCase entryService;
    @Autowired InventoryUnitUseCase unitService;
    @Autowired com.example.msinventory.application.port.in.PurchaseEntryUseCase purchaseService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ActorProvider actorProvider;
    @MockitoSpyBean InventoryMovementRepositoryPort movementPort;
    @MockitoSpyBean com.example.msinventory.application.port.out.InventoryUnitRepositoryPort unitPort;

    private int keySeq;

    @BeforeEach
    void setUp() {
        when(actorProvider.currentActor()).thenReturn("op-1");
    }

    @AfterEach
    void cleanup() {
        org.mockito.Mockito.reset(movementPort, unitPort);
        jdbc.update("delete from purchase_operations");
        jdbc.update("delete from inventory_movements");
        jdbc.update("delete from inventory_units");
        jdbc.update("delete from inventory_entries");
    }

    private Long register(InventoryUnitStatus status, LocalDate eta) {
        RegisterEntryCommand cmd = new RegisterEntryCommand(EntryType.STOCK_PURCHASE, "SKU-1", "NEW", Warehouse.LIMA, 1,
                new BigDecimal("12.50"), null, status, eta, null, null, null, null, null);
        return entryService.register("k-" + (keySeq++), "h", cmd).units().get(0).getId();
    }

    private Long available() {
        return register(InventoryUnitStatus.AVAILABLE, null);
    }

    private List<InventoryMovement> movs(Long id) {
        return unitService.movements(id);
    }

    private long count(Long id, MovementType type) {
        return movs(id).stream().filter(m -> m.type() == type).count();
    }

    @Test
    void allocate_available_writesExitMovement() {
        Long id = available();

        InventoryUnit u = service.allocate(id, 7L, "SKU-1");

        assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.ALLOCATED);
        assertThat(u.getAllocatedOrderItemId()).isEqualTo(7L);
        List<InventoryMovement> ms = movs(id);
        assertThat(ms).hasSize(2);
        InventoryMovement m = ms.get(1);
        assertThat(m.type()).isEqualTo(MovementType.EXIT_ORDER_ALLOCATION);
        assertThat(m.fromWarehouse()).isEqualTo(Warehouse.LIMA);
        assertThat(m.toWarehouse()).isNull();
        assertThat(m.reference()).isEqualTo("order-item:7");
        assertThat(m.performedBy()).isEqualTo("op-1");
        assertThat(m.unitCost()).isEqualByComparingTo("12.50");
        assertThat(unitService.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.ALLOCATED);
    }

    @Test
    void allocate_sameOrderItemTwice_idempotent() {
        Long id = available();

        service.allocate(id, 7L, "SKU-1");
        InventoryUnit again = service.allocate(id, 7L, "SKU-1");

        assertThat(again.getStatus()).isEqualTo(InventoryUnitStatus.ALLOCATED);
        assertThat(again.getAllocatedOrderItemId()).isEqualTo(7L);
        assertThat(count(id, MovementType.EXIT_ORDER_ALLOCATION)).isEqualTo(1);
        assertThat(movs(id)).hasSize(2);
    }

    @Test
    void allocate_toOtherOrderItem_conflict() {
        Long id = available();
        service.allocate(id, 7L, "SKU-1");

        assertThatThrownBy(() -> service.allocate(id, 8L, "SKU-1")).isInstanceOf(AllocationConflictException.class);

        assertThat(unitService.findOne(id).getAllocatedOrderItemId()).isEqualTo(7L);
        assertThat(count(id, MovementType.EXIT_ORDER_ALLOCATION)).isEqualTo(1);
    }

    @Test
    void allocate_skuMismatch_conflict() {
        Long id = available();

        assertThatThrownBy(() -> service.allocate(id, 7L, "OTRO")).isInstanceOf(SkuMismatchException.class);

        assertThat(unitService.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(movs(id)).hasSize(1);
    }

    @Test
    void allocate_inTransit_conflict() {
        Long id = register(InventoryUnitStatus.IN_TRANSIT, LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> service.allocate(id, 7L, "SKU-1")).isInstanceOf(AllocationConflictException.class);

        assertThat(unitService.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.IN_TRANSIT);
        assertThat(movs(id)).hasSize(1);
    }

    @Test
    void allocate_unknownUnit_notFound() {
        assertThatThrownBy(() -> service.allocate(999L, 7L, "SKU-1")).isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    void orderItemAlreadyCovered_conflict() {
        Long first = available();
        Long second = available();
        service.allocate(first, 7L, "SKU-1");

        assertThatThrownBy(() -> service.allocate(second, 7L, "SKU-1")).isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("cubierto por la unidad " + first);

        assertThat(unitService.findOne(second).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(movs(second)).hasSize(1);
    }

    @Test
    void release_thenAvailable_writesReleased() {
        Long id = available();
        service.allocate(id, 7L, "SKU-1");

        service.release(id, 7L);

        InventoryUnit u = unitService.findOne(id);
        assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(u.getAllocatedOrderItemId()).isNull();
        List<InventoryMovement> ms = movs(id);
        assertThat(ms).hasSize(3);
        InventoryMovement m = ms.get(2);
        assertThat(m.type()).isEqualTo(MovementType.ALLOCATION_RELEASED);
        assertThat(m.toWarehouse()).isEqualTo(Warehouse.LIMA);
        assertThat(m.fromWarehouse()).isNull();
        assertThat(m.reference()).isEqualTo("order-item:7");
        assertThat(m.performedBy()).isEqualTo("op-1");
    }

    @Test
    void release_twice_noop() {
        Long id = available();
        service.allocate(id, 7L, "SKU-1");
        service.release(id, 7L);

        service.release(id, 7L);

        assertThat(count(id, MovementType.ALLOCATION_RELEASED)).isEqualTo(1);
        assertThat(unitService.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
    }

    @Test
    void release_freeUnitNeverAllocated_noop() {
        Long id = available();

        service.release(id, 7L);

        assertThat(movs(id)).hasSize(1);
    }

    @Test
    void release_byOtherOrderItem_conflict() {
        Long id = available();
        service.allocate(id, 7L, "SKU-1");

        assertThatThrownBy(() -> service.release(id, 8L)).isInstanceOf(AllocationConflictException.class);

        assertThat(unitService.findOne(id).getAllocatedOrderItemId()).isEqualTo(7L);
        assertThat(count(id, MovementType.ALLOCATION_RELEASED)).isZero();
    }

    @Test
    void release_writtenOffUnit_invalidTransition() {
        Long id = available();
        unitService.writeOff(id, "danado");

        assertThatThrownBy(() -> service.release(id, 7L)).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void release_unknownUnit_notFound() {
        assertThatThrownBy(() -> service.release(999L, 7L)).isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    void allocate_nonUniquenessIntegrityFailure_isNotTurnedIntoConflict() {
        Long id = available();
        DataIntegrityViolationException boom = new DataIntegrityViolationException("not null violation");
        org.mockito.Mockito.doThrow(boom).when(movementPort).append(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.allocate(id, 7L, "SKU-1")).isSameAs(boom);

        org.mockito.Mockito.reset(movementPort);
        assertThat(unitService.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
    }

    private List<Long> purchase(int quantity, List<Long> reservations) {
        return purchaseService.create("p-" + (keySeq++), "h", new com.example.msinventory.application.port.in
                        .PurchaseEntryUseCase.CreatePurchaseEntryCommand(900L, 3L, "SKU-1", "NEW", quantity,
                        new BigDecimal("12.50"), null, LocalDate.now().plusDays(5), reservations))
                .units().stream().map(InventoryUnit::getId).toList();
    }

    @Test
    void release_inTransitReservation_204_reservationReleasedMovement() {
        List<Long> ids = purchase(2, List.of(7L));

        service.release(ids.get(0), 7L);

        InventoryUnit u = unitService.findOne(ids.get(0));
        assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.IN_TRANSIT);
        assertThat(u.getAllocatedOrderItemId()).isNull();
        assertThat(count(ids.get(0), MovementType.RESERVATION_RELEASED)).isEqualTo(1);
        assertThat(movs(ids.get(0)).get(1).reference()).isEqualTo("order-item:7");
        // sin reserva (y repetido): no-op
        service.release(ids.get(0), 7L);
        service.release(ids.get(1), 7L);
        assertThat(count(ids.get(0), MovementType.RESERVATION_RELEASED)).isEqualTo(1);
        assertThat(count(ids.get(1), MovementType.RESERVATION_RELEASED)).isZero();
    }

    @Test
    void release_inTransitReservedForOtherItem_conflict() {
        List<Long> ids = purchase(1, List.of(7L));
        assertThatThrownBy(() -> service.release(ids.get(0), 8L)).isInstanceOf(AllocationConflictException.class);
    }

    @Test
    void release_notReceivedUnit_noop() {
        List<Long> ids = purchase(1, List.of(7L));
        purchaseService.markNotReceived("nr-rel", "h", 900L, 1, List.of(7L), "perdida");

        service.release(ids.get(0), 7L);

        assertThat(unitService.findOne(ids.get(0)).getStatus()).isEqualTo(InventoryUnitStatus.NOT_RECEIVED);
        assertThat(count(ids.get(0), MovementType.RESERVATION_RELEASED)).isZero();
    }

    /** Carrera con una reserva de compra: el indice unico rechaza el update y se responde ALLOCATION_CONFLICT. */
    @Test
    void allocate_uniqueRaceWithPurchaseReservation_allocationConflict() {
        List<Long> reserved = purchase(1, List.of(7L));
        Long id = available();
        org.mockito.Mockito.doReturn(java.util.Optional.empty()).doCallRealMethod()
                .when(unitPort).findByAllocatedOrderItemId(7L);

        assertThatThrownBy(() -> service.allocate(id, 7L, "SKU-1")).isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("cubierto por la unidad " + reserved.get(0));

        assertThat(unitService.findOne(id).getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
    }
}
