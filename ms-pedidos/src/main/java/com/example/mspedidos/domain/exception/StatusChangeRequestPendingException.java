package com.example.mspedidos.domain.exception;

public class StatusChangeRequestPendingException extends BusinessRuleException {

    public StatusChangeRequestPendingException(String message) {
        super(message);
    }
}
