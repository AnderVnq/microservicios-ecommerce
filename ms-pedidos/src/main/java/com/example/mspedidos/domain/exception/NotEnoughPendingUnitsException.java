package com.example.mspedidos.domain.exception;

public class NotEnoughPendingUnitsException extends BusinessRuleException {

    public NotEnoughPendingUnitsException(String message) {
        super(message);
    }
}
