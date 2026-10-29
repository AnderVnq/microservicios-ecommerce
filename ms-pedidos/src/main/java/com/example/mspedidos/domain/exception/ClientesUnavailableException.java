package com.example.mspedidos.domain.exception;

public class ClientesUnavailableException extends RuntimeException {

    public ClientesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
