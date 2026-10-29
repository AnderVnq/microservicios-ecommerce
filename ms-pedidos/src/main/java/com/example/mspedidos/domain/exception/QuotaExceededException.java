package com.example.mspedidos.domain.exception;

public class QuotaExceededException extends BusinessRuleException {

    public QuotaExceededException(String message) {
        super(message);
    }
}
