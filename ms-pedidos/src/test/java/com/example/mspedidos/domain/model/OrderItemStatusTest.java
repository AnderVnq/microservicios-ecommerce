package com.example.mspedidos.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.example.mspedidos.domain.model.OrderItemStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderItemStatusTest {

    private static final Map<OrderItemStatus, Set<OrderItemStatus>> SUCCESSORS = Map.ofEntries(
            Map.entry(PROCESSING, EnumSet.of(PURCHASED, CANCELLED)),
            Map.entry(PURCHASED, EnumSet.of(RECEIVED_AT_WAREHOUSE, CANCELLED)),
            Map.entry(RECEIVED_AT_WAREHOUSE, EnumSet.of(READY_TO_SHIP, CANCELLED)),
            Map.entry(READY_TO_SHIP, EnumSet.of(SHIPPED, CANCELLED)),
            Map.entry(SHIPPED, EnumSet.of(ARRIVED_LIMA, CANCELLED)),
            Map.entry(ARRIVED_LIMA, EnumSet.of(CUSTOMS_HOLD, RECEIVED_LIMA, CANCELLED)),
            Map.entry(CUSTOMS_HOLD, EnumSet.of(RECEIVED_LIMA, CANCELLED)),
            Map.entry(RECEIVED_LIMA, EnumSet.of(DISPATCHED, CANCELLED)),
            Map.entry(DISPATCHED, EnumSet.of(DELIVERED, CANCELLED)),
            Map.entry(DELIVERED, EnumSet.of(CUSTOMER_RECEIVED, CANCELLED)),
            Map.entry(CUSTOMER_RECEIVED, EnumSet.noneOf(OrderItemStatus.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(OrderItemStatus.class)));

    @ParameterizedTest
    @EnumSource(OrderItemStatus.class)
    void canMoveTo_matchesExpectedMatrix(OrderItemStatus from) {
        for (OrderItemStatus to : OrderItemStatus.values()) {
            assertThat(from.canMoveTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(SUCCESSORS.get(from).contains(to));
        }
    }

    @ParameterizedTest
    @EnumSource(OrderItemStatus.class)
    void isTerminal_onlyCustomerReceivedAndCancelled(OrderItemStatus status) {
        assertThat(status.isTerminal()).isEqualTo(status == CUSTOMER_RECEIVED || status == CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(OrderItemStatus.class)
    void isBeforeReadyToShip_onlyEarlyStatuses(OrderItemStatus status) {
        assertThat(status.isBeforeReadyToShip())
                .isEqualTo(EnumSet.of(PROCESSING, PURCHASED, RECEIVED_AT_WAREHOUSE).contains(status));
    }
}
