package com.example.mspedidos.domain.exception;

public class SupplierNameTakenException extends BusinessRuleException {

    public SupplierNameTakenException(String message) {
        super(message);
    }
}
