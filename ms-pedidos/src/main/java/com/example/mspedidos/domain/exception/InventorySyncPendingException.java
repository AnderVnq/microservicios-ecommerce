package com.example.mspedidos.domain.exception;

public class InventorySyncPendingException extends BusinessRuleException {

    public InventorySyncPendingException(String message) {
        super(message);
    }
}
