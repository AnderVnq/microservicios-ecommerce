package com.example.ms_auth.domain.exception;

public class AplicacionExternaNotFoundException extends RuntimeException {

    public AplicacionExternaNotFoundException(Long id) {
        super("Aplicacion externa %d no existe".formatted(id));
    }
}
