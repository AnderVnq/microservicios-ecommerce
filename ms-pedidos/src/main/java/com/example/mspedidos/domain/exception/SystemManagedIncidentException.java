package com.example.mspedidos.domain.exception;

public class SystemManagedIncidentException extends BusinessRuleException {

    public SystemManagedIncidentException(String message) {
        super(message);
    }
}
