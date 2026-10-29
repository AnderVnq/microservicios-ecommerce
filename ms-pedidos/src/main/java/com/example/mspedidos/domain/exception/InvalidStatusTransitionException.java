package com.example.mspedidos.domain.exception;

public class InvalidStatusTransitionException extends BusinessRuleException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
