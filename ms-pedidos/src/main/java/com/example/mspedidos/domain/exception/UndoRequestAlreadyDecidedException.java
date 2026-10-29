package com.example.mspedidos.domain.exception;

public class UndoRequestAlreadyDecidedException extends BusinessRuleException {

    public UndoRequestAlreadyDecidedException(String message) {
        super(message);
    }
}
