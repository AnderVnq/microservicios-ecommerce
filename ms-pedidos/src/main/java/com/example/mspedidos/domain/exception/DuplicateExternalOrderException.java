package com.example.mspedidos.domain.exception;

public class DuplicateExternalOrderException extends BusinessRuleException {

    public DuplicateExternalOrderException(String externalOrderId) {
        super("Ya existe una orden con el id externo: " + externalOrderId);
    }
}
