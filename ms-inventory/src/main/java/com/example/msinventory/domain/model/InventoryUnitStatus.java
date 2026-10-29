package com.example.msinventory.domain.model;

public enum InventoryUnitStatus {
    IN_TRANSIT, AVAILABLE, ALLOCATED, RETURNED_TO_SUPPLIER, WRITTEN_OFF, ADJUSTED_OUT, NOT_RECEIVED;

    public boolean canMoveTo(InventoryUnitStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case IN_TRANSIT -> next == AVAILABLE || next == WRITTEN_OFF || next == NOT_RECEIVED;
            case AVAILABLE -> next == IN_TRANSIT || next == ALLOCATED || next == RETURNED_TO_SUPPLIER
                    || next == WRITTEN_OFF || next == ADJUSTED_OUT;
            case ALLOCATED -> next == AVAILABLE;
            case RETURNED_TO_SUPPLIER, WRITTEN_OFF, ADJUSTED_OUT, NOT_RECEIVED -> false;
        };
    }
}
