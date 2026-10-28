package com.example.ms_auth.domain.exception;

public class PermisoAlreadyExistsException extends RuntimeException {

    public PermisoAlreadyExistsException(String recurso, String accion) {
        super("Ya existe el permiso %s:%s".formatted(recurso, accion));
    }
}
