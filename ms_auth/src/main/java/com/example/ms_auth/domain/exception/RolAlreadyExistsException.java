package com.example.ms_auth.domain.exception;

public class RolAlreadyExistsException extends RuntimeException {

    public RolAlreadyExistsException(String nombre) {
        super("Ya existe un rol con nombre " + nombre);
    }
}
