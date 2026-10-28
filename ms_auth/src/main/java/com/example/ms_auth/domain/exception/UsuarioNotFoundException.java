package com.example.ms_auth.domain.exception;

public class UsuarioNotFoundException extends RuntimeException {

    public UsuarioNotFoundException(Long id) {
        super("Usuario %d no existe".formatted(id));
    }
}
