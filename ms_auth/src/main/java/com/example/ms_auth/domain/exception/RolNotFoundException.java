package com.example.ms_auth.domain.exception;

public class RolNotFoundException extends RuntimeException {

    public RolNotFoundException(Long id) {
        super("Rol %d no existe".formatted(id));
    }
}
