package com.example.mspedidos.domain.exception;

public class IncidentNotFoundException extends NotFoundException {

    public IncidentNotFoundException(Long id) {
        super("Incidencia no encontrada: " + id);
    }
}
