package com.example.mspedidos.domain.exception;

public class UndoRequestNotFoundException extends NotFoundException {

    public UndoRequestNotFoundException(Long id) {
        super("Solicitud de deshacer no encontrada: " + id);
    }
}
