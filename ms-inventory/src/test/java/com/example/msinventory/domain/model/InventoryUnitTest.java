package com.example.msinventory.domain.model;

import com.example.msinventory.domain.exception.AllocationConflictException;
import com.example.msinventory.domain.exception.InvalidUnitTransitionException;
import com.example.msinventory.domain.exception.SkuMismatchException;
import com.example.msinventory.domain.exception.UsePurchaseFlowException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.example.msinventory.domain.model.InventoryUnitStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryUnitTest {

    private static final LocalDate ETA = LocalDate.of(2026, 11, 10);

    private InventoryUnit unit(InventoryUnitStatus status, LocalDate eta) {
        return InventoryUnit.newUnit("SKU-1", "NEW", Warehouse.USA, status, new BigDecimal("10.50"), "USD",
                EntryType.STOCK_PURCHASE, "INV-1", "AMZ-1", "ref", eta, null, 7L);
    }

    private InventoryUnit purchaseUnit() {
        return InventoryUnit.newUnit("SKU-1", "NEW", Warehouse.USA, IN_TRANSIT, new BigDecimal("10.50"), "USD",
                EntryType.SUPPLIER_PURCHASE, null, null, "ref", ETA, null, 7L, 3L, 99L);
    }

    private InventoryUnit available() {
        return unit(AVAILABLE, null);
    }

    @Test
    void newInTransit_requiresExpectedArrival() {
        assertThatThrownBy(() -> unit(IN_TRANSIT, null)).isInstanceOf(IllegalArgumentException.class);
        InventoryUnit u = unit(IN_TRANSIT, ETA);
        assertThat(u.getStatus()).isEqualTo(IN_TRANSIT);
        assertThat(u.getArrivedAt()).isNull();
        assertThat(u.getExpectedArrivalDate()).isEqualTo(ETA);
        assertThat(u.getEntryId()).isEqualTo(7L);
    }

    @Test
    void newUnit_availableSetsArrivedAt_otherStatusesRejected() {
        InventoryUnit u = available();
        assertThat(u.getStatus()).isEqualTo(AVAILABLE);
        assertThat(u.getArrivedAt()).isNotNull();
        assertThat(u.getCreatedAt()).isNotNull();
        assertThatThrownBy(() -> unit(ALLOCATED, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arrive_twice_secondIsNoop() {
        InventoryUnit u = unit(IN_TRANSIT, ETA);
        assertThat(u.arrive()).isTrue();
        assertThat(u.getStatus()).isEqualTo(AVAILABLE);
        assertThat(u.getArrivedAt()).isNotNull();
        var arrivedAt = u.getArrivedAt();
        assertThat(u.arrive()).isFalse();
        assertThat(u.getArrivedAt()).isEqualTo(arrivedAt);
    }

    @Test
    void arrive_fromOtherStatus_throws() {
        InventoryUnit u = available();
        u.allocate(1L, "SKU-1");
        assertThatThrownBy(u::arrive).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void transfer_onlyFromAvailable_toOtherWarehouse() {
        InventoryUnit u = available();
        assertThatThrownBy(() -> u.transferTo(Warehouse.USA, ETA)).isInstanceOf(InvalidUnitTransitionException.class);
        assertThat(u.getStatus()).isEqualTo(AVAILABLE);

        Warehouse origin = u.transferTo(Warehouse.LIMA, ETA);
        assertThat(origin).isEqualTo(Warehouse.USA);
        assertThat(u.getStatus()).isEqualTo(IN_TRANSIT);
        assertThat(u.getWarehouse()).isEqualTo(Warehouse.LIMA);
        assertThat(u.getExpectedArrivalDate()).isEqualTo(ETA);
        assertThat(u.getArrivedAt()).isNull();

        assertThatThrownBy(() -> u.transferTo(Warehouse.USA, ETA)).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void transfer_requiresExpectedArrival() {
        InventoryUnit u = available();
        assertThatThrownBy(() -> u.transferTo(Warehouse.LIMA, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allocate_setsOrderItem() {
        InventoryUnit u = available();
        u.allocate(5L, "SKU-1");
        assertThat(u.getStatus()).isEqualTo(ALLOCATED);
        assertThat(u.getAllocatedOrderItemId()).isEqualTo(5L);
    }

    @Test
    void allocate_skuMismatch_throws() {
        InventoryUnit u = available();
        assertThatThrownBy(() -> u.allocate(5L, "OTHER")).isInstanceOf(SkuMismatchException.class);
        assertThat(u.getStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    void allocate_inTransit_throws() {
        InventoryUnit u = unit(IN_TRANSIT, ETA);
        assertThatThrownBy(() -> u.allocate(5L, "SKU-1")).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void release_returnsToAvailableAndClearsId() {
        InventoryUnit u = available();
        u.allocate(5L, "SKU-1");
        u.release(5L);
        assertThat(u.getStatus()).isEqualTo(AVAILABLE);
        assertThat(u.getAllocatedOrderItemId()).isNull();
    }

    @Test
    void release_byOtherOrderItem_throws() {
        InventoryUnit u = available();
        u.allocate(5L, "SKU-1");
        assertThatThrownBy(() -> u.release(6L)).isInstanceOf(AllocationConflictException.class);
        assertThat(u.getStatus()).isEqualTo(ALLOCATED);
        assertThat(u.getAllocatedOrderItemId()).isEqualTo(5L);
    }

    @Test
    void release_notAllocated_throws() {
        InventoryUnit u = available();
        assertThatThrownBy(() -> u.release(5L)).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void exit_sameTargetTwice_isNoop_otherExitThrows() {
        InventoryUnit u = available();
        assertThat(u.exit(RETURNED_TO_SUPPLIER)).isTrue();
        assertThat(u.getStatus()).isEqualTo(RETURNED_TO_SUPPLIER);
        assertThat(u.exit(RETURNED_TO_SUPPLIER)).isFalse();
        assertThatThrownBy(() -> u.exit(WRITTEN_OFF)).isInstanceOf(InvalidUnitTransitionException.class);
        assertThat(u.getStatus()).isEqualTo(RETURNED_TO_SUPPLIER);
    }

    @Test
    void exit_invalidTarget_throws() {
        InventoryUnit u = available();
        assertThatThrownBy(() -> u.exit(ALLOCATED)).isInstanceOf(InvalidUnitTransitionException.class);
        assertThatThrownBy(() -> u.exit(AVAILABLE)).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void exit_fromAllocated_throws() {
        InventoryUnit u = available();
        u.allocate(5L, "SKU-1");
        assertThatThrownBy(() -> u.exit(ADJUSTED_OUT)).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void writeOff_allowedFromInTransit() {
        InventoryUnit u = unit(IN_TRANSIT, ETA);
        assertThat(u.exit(WRITTEN_OFF)).isTrue();
        assertThat(u.getStatus()).isEqualTo(WRITTEN_OFF);
    }

    @Test
    void adjustOut_notAllowedFromInTransit() {
        InventoryUnit u = unit(IN_TRANSIT, ETA);
        assertThatThrownBy(() -> u.exit(ADJUSTED_OUT)).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void updateDates_setsBoth() {
        InventoryUnit u = unit(IN_TRANSIT, ETA);
        u.updateDates(ETA.plusDays(3), ETA.plusDays(30));
        assertThat(u.getExpectedArrivalDate()).isEqualTo(ETA.plusDays(3));
        assertThat(u.getSupplierReturnDeadline()).isEqualTo(ETA.plusDays(30));
    }

    @Test
    void reserve_onlyInTransitPurchaseUnit() {
        InventoryUnit u = purchaseUnit();
        assertThat(u.isUnarrivedPurchaseUnit()).isTrue();
        assertThat(u.getPurchaseId()).isEqualTo(99L);
        assertThat(u.getSupplierId()).isEqualTo(3L);
        u.reserve(5L);
        assertThat(u.getAllocatedOrderItemId()).isEqualTo(5L);
        assertThat(u.getStatus()).isEqualTo(IN_TRANSIT);
        u.reserve(5L);
        assertThat(u.getAllocatedOrderItemId()).isEqualTo(5L);

        assertThatThrownBy(() -> unit(IN_TRANSIT, ETA).reserve(5L)).isInstanceOf(InvalidUnitTransitionException.class);
        assertThatThrownBy(() -> available().reserve(5L)).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void reserve_otherOrderItem_conflict() {
        InventoryUnit u = purchaseUnit();
        u.reserve(5L);
        assertThatThrownBy(() -> u.reserve(6L)).isInstanceOf(AllocationConflictException.class);
        assertThat(u.getAllocatedOrderItemId()).isEqualTo(5L);
    }

    @Test
    void releaseReservation_thenFree_secondCallFalse() {
        InventoryUnit u = purchaseUnit();
        u.reserve(5L);
        assertThatThrownBy(() -> u.releaseReservation(6L)).isInstanceOf(AllocationConflictException.class);
        assertThat(u.releaseReservation(5L)).isTrue();
        assertThat(u.getAllocatedOrderItemId()).isNull();
        assertThat(u.releaseReservation(5L)).isFalse();
        assertThat(u.getStatus()).isEqualTo(IN_TRANSIT);
    }

    @Test
    void arriveFromPurchase_reservedBecomesAllocated() {
        InventoryUnit u = purchaseUnit();
        u.reserve(5L);
        assertThat(u.arriveFromPurchase()).isTrue();
        assertThat(u.getStatus()).isEqualTo(ALLOCATED);
        assertThat(u.getAllocatedOrderItemId()).isEqualTo(5L);
        assertThat(u.getArrivedAt()).isNotNull();
        assertThat(u.arriveFromPurchase()).isFalse();
        assertThat(u.getStatus()).isEqualTo(ALLOCATED);
    }

    @Test
    void arriveFromPurchase_freeBecomesAvailable() {
        InventoryUnit u = purchaseUnit();
        assertThat(u.arriveFromPurchase()).isTrue();
        assertThat(u.getStatus()).isEqualTo(AVAILABLE);
        assertThat(u.getArrivedAt()).isNotNull();
        assertThat(u.arriveFromPurchase()).isFalse();
        assertThatThrownBy(() -> unit(IN_TRANSIT, ETA).arriveFromPurchase())
                .isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void markNotReceived_clearsReservation_idempotent() {
        InventoryUnit u = purchaseUnit();
        u.reserve(5L);
        assertThat(u.markNotReceived()).isTrue();
        assertThat(u.getStatus()).isEqualTo(NOT_RECEIVED);
        assertThat(u.getAllocatedOrderItemId()).isNull();
        assertThat(u.markNotReceived()).isFalse();
    }

    @Test
    void genericArriveOrWriteOff_onUnarrivedPurchaseUnit_throwsUsePurchaseFlow() {
        InventoryUnit u = purchaseUnit();
        assertThatThrownBy(u::arrive).isInstanceOf(UsePurchaseFlowException.class);
        assertThatThrownBy(() -> u.exit(WRITTEN_OFF)).isInstanceOf(UsePurchaseFlowException.class);
        assertThatThrownBy(() -> u.transferTo(Warehouse.LIMA, ETA)).isInstanceOf(UsePurchaseFlowException.class);
        assertThat(u.getStatus()).isEqualTo(IN_TRANSIT);
    }

    @Test
    void reserve_nullOrderItem_throwsIllegalArgument() {
        assertThatThrownBy(() -> purchaseUnit().reserve(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markNotReceived_nonPurchaseUnit_throws() {
        assertThatThrownBy(() -> unit(IN_TRANSIT, ETA).markNotReceived())
                .isInstanceOf(InvalidUnitTransitionException.class);
        assertThatThrownBy(() -> available().markNotReceived()).isInstanceOf(InvalidUnitTransitionException.class);
    }

    @Test
    void releaseReservation_neverReserved_false() {
        assertThat(purchaseUnit().releaseReservation(5L)).isFalse();
    }
}
