package com.example.mspedidos.domain.model;

public enum OrderItemStatus {
    PROCESSING, PURCHASED, RECEIVED_AT_WAREHOUSE, READY_TO_SHIP, SHIPPED, ARRIVED_LIMA,
    CUSTOMS_HOLD, RECEIVED_LIMA, DISPATCHED, DELIVERED, CUSTOMER_RECEIVED, CANCELLED;

    public boolean isTerminal() {
        return this == CUSTOMER_RECEIVED || this == CANCELLED;
    }

    public boolean canMoveTo(OrderItemStatus next) {
        if (next == null || isTerminal()) {
            return false;
        }
        if (next == CANCELLED) {
            return true;
        }
        return switch (this) {
            case PROCESSING -> next == PURCHASED;
            case PURCHASED -> next == RECEIVED_AT_WAREHOUSE;
            case RECEIVED_AT_WAREHOUSE -> next == READY_TO_SHIP;
            case READY_TO_SHIP -> next == SHIPPED;
            case SHIPPED -> next == ARRIVED_LIMA;
            case ARRIVED_LIMA -> next == CUSTOMS_HOLD || next == RECEIVED_LIMA;
            case CUSTOMS_HOLD -> next == RECEIVED_LIMA;
            case RECEIVED_LIMA -> next == DISPATCHED;
            case DISPATCHED -> next == DELIVERED;
            case DELIVERED -> next == CUSTOMER_RECEIVED;
            default -> false;
        };
    }

    public boolean isBeforeReadyToShip() {
        return this == PROCESSING || this == PURCHASED || this == RECEIVED_AT_WAREHOUSE;
    }
}
