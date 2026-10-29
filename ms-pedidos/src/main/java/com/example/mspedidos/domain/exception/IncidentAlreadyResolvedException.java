package com.example.mspedidos.domain.exception;

public class IncidentAlreadyResolvedException extends BusinessRuleException {

    public IncidentAlreadyResolvedException(Long id) {
        super("La incidencia ya fue resuelta: " + id);
    }
}
