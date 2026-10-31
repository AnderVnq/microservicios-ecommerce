package com.example.msproducts.domain.exception;

/** Base de los recursos inexistentes (HTTP 404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
