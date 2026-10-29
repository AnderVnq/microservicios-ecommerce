package com.example.mspedidos.domain.exception;

public class StatusChangeRequestNotFoundException extends NotFoundException {

    public StatusChangeRequestNotFoundException(Long id) {
        super("Solicitud de deshacer no encontrada: " + id);
    }
}
