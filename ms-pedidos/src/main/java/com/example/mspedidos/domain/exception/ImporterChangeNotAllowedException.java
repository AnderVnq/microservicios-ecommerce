package com.example.mspedidos.domain.exception;

public class ImporterChangeNotAllowedException extends BusinessRuleException {

    public ImporterChangeNotAllowedException(String message) {
        super(message);
    }
}
