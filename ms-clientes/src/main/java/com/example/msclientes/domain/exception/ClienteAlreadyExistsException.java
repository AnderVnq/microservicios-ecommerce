package com.example.msclientes.domain.exception;

public class ClienteAlreadyExistsException extends RuntimeException {

    public ClienteAlreadyExistsException(String numeroDocumento) {
        super("Ya existe un cliente con documento " + numeroDocumento);
    }
}
