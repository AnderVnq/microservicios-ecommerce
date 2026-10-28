package com.example.ms_auth.domain.exception;

public class AplicacionExternaAlreadyExistsException extends RuntimeException {

    public AplicacionExternaAlreadyExistsException(String keycloakClientId) {
        super("Ya existe una aplicacion externa para keycloakClientId " + keycloakClientId);
    }
}
