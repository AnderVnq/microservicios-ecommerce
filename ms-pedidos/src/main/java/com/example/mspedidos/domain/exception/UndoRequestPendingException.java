package com.example.mspedidos.domain.exception;

public class UndoRequestPendingException extends BusinessRuleException {

    public UndoRequestPendingException(String message) {
        super(message);
    }
}
