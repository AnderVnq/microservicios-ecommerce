package com.example.ms_auth.domain.exception;

public class UsuarioAlreadyExistsException extends RuntimeException {

    public UsuarioAlreadyExistsException(String keycloakUserId) {
        super("Ya existe un usuario para keycloakUserId " + keycloakUserId);
    }
}
