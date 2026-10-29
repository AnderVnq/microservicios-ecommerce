package com.example.msinventory.domain.model;

import org.junit.jupiter.api.Test;

import static com.example.msinventory.domain.model.MovementType.*;
import static org.assertj.core.api.Assertions.assertThat;

class MovementTypeTest {

    @Test
    void entryFor_mapsEachEntryType() {
        assertThat(MovementType.entryFor(EntryType.STOCK_PURCHASE)).isEqualTo(ENTRY_STOCK_PURCHASE);
        assertThat(MovementType.entryFor(EntryType.CUSTOMER_RETURN)).isEqualTo(ENTRY_CUSTOMER_RETURN);
        assertThat(MovementType.entryFor(EntryType.ADJUSTMENT_IN)).isEqualTo(ENTRY_ADJUSTMENT);
    }

    @Test
    void direction_matchesSpec() {
        assertThat(ENTRY_STOCK_PURCHASE.direction()).isEqualTo(Direction.IN);
        assertThat(ENTRY_CUSTOMER_RETURN.direction()).isEqualTo(Direction.IN);
        assertThat(ENTRY_ADJUSTMENT.direction()).isEqualTo(Direction.IN);
        assertThat(ALLOCATION_RELEASED.direction()).isEqualTo(Direction.IN);
        assertThat(EXIT_ORDER_ALLOCATION.direction()).isEqualTo(Direction.OUT);
        assertThat(EXIT_SUPPLIER_RETURN.direction()).isEqualTo(Direction.OUT);
        assertThat(EXIT_WRITE_OFF.direction()).isEqualTo(Direction.OUT);
        assertThat(EXIT_ADJUSTMENT.direction()).isEqualTo(Direction.OUT);
        assertThat(ARRIVAL.direction()).isEqualTo(Direction.INTERNAL);
        assertThat(TRANSFER.direction()).isEqualTo(Direction.INTERNAL);
    }
}
