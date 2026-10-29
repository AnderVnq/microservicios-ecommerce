package com.example.mspedidos.domain.exception;

public class OrderNotFoundException extends NotFoundException {

    public OrderNotFoundException(Long id) {
        super("Orden no encontrada: " + id);
    }
}
