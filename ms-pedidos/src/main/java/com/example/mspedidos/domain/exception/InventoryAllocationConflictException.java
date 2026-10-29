package com.example.mspedidos.domain.exception;

public class InventoryAllocationConflictException extends BusinessRuleException {

    public InventoryAllocationConflictException(String message) {
        super(message);
    }
}
