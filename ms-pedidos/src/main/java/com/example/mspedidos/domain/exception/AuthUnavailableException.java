package com.example.mspedidos.domain.exception;

public class AuthUnavailableException extends RuntimeException {

    public AuthUnavailableException(String message) {
        super(message);
    }
}
