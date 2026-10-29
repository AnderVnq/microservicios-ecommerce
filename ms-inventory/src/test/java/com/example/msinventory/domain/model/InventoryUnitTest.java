package com.example.msinventory.domain.model;

import com.example.msinventory.domain.exception.AllocationConflictException;
import com.example.msinventory.domain.exception.InvalidUnitTransitionException;
import com.example.msinventory.domain.exception.SkuMismatchException;
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
}
