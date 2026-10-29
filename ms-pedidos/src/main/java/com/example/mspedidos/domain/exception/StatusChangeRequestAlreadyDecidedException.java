package com.example.mspedidos.domain.exception;

public class StatusChangeRequestAlreadyDecidedException extends BusinessRuleException {

    public StatusChangeRequestAlreadyDecidedException(String message) {
        super(message);
    }
}
