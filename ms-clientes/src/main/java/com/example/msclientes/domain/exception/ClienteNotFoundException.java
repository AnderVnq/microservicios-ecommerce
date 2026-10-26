package com.example.msclientes.domain.exception;

public class ClienteNotFoundException extends RuntimeException {

    public ClienteNotFoundException(Long id) {
        super("Cliente %d no existe".formatted(id));
    }
}
