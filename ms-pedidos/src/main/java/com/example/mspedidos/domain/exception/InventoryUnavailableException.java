package com.example.mspedidos.domain.exception;

public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String message) {
        super(message);
    }
}
