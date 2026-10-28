package com.example.ms_auth.domain.exception;

public class PermisoNotFoundException extends RuntimeException {

    public PermisoNotFoundException(Long id) {
        super("Permiso %d no existe".formatted(id));
    }
}
