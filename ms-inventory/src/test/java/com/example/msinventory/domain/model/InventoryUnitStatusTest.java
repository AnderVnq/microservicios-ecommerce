package com.example.msinventory.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.example.msinventory.domain.model.InventoryUnitStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class InventoryUnitStatusTest {

    private static final Map<InventoryUnitStatus, Set<InventoryUnitStatus>> SUCCESSORS = Map.ofEntries(
            Map.entry(IN_TRANSIT, EnumSet.of(AVAILABLE, WRITTEN_OFF, NOT_RECEIVED)),
            Map.entry(AVAILABLE, EnumSet.of(IN_TRANSIT, ALLOCATED, RETURNED_TO_SUPPLIER, WRITTEN_OFF, ADJUSTED_OUT)),
            Map.entry(ALLOCATED, EnumSet.of(AVAILABLE)),
            Map.entry(RETURNED_TO_SUPPLIER, EnumSet.noneOf(InventoryUnitStatus.class)),
            Map.entry(WRITTEN_OFF, EnumSet.noneOf(InventoryUnitStatus.class)),
            Map.entry(ADJUSTED_OUT, EnumSet.noneOf(InventoryUnitStatus.class)),
            Map.entry(NOT_RECEIVED, EnumSet.noneOf(InventoryUnitStatus.class)));

    @ParameterizedTest
    @EnumSource(InventoryUnitStatus.class)
    void canMoveTo_matchesSpecMatrix(InventoryUnitStatus from) {
        for (InventoryUnitStatus to : InventoryUnitStatus.values()) {
            assertThat(from.canMoveTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(SUCCESSORS.get(from).contains(to));
        }
    }

    @ParameterizedTest
    @EnumSource(InventoryUnitStatus.class)
    void canMoveTo_null_isFalse(InventoryUnitStatus from) {
        assertThat(from.canMoveTo(null)).isFalse();
    }
}
