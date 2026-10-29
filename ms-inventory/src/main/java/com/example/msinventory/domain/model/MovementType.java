package com.example.msinventory.domain.model;

public enum MovementType {
    ENTRY_STOCK_PURCHASE(Direction.IN),
    ENTRY_CUSTOMER_RETURN(Direction.IN),
    ENTRY_ADJUSTMENT(Direction.IN),
    EXIT_ORDER_ALLOCATION(Direction.OUT),
    EXIT_SUPPLIER_RETURN(Direction.OUT),
    EXIT_WRITE_OFF(Direction.OUT),
    EXIT_ADJUSTMENT(Direction.OUT),
    ALLOCATION_RELEASED(Direction.IN),
    ARRIVAL(Direction.INTERNAL),
    TRANSFER(Direction.INTERNAL);

    private final Direction direction;

    MovementType(Direction direction) {
        this.direction = direction;
    }

    public Direction direction() {
        return direction;
    }

    public static MovementType entryFor(EntryType entryType) {
        return switch (entryType) {
            case STOCK_PURCHASE -> ENTRY_STOCK_PURCHASE;
            case CUSTOMER_RETURN -> ENTRY_CUSTOMER_RETURN;
            case ADJUSTMENT_IN -> ENTRY_ADJUSTMENT;
        };
    }
}
