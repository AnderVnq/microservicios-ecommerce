package com.example.mspedidos.domain.exception;

public class UndoNotAllowedException extends BusinessRuleException {

    public UndoNotAllowedException(String message) {
        super(message);
    }
}
