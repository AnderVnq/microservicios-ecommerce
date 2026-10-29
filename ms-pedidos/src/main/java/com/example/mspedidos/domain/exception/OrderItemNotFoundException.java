package com.example.mspedidos.domain.exception;

public class OrderItemNotFoundException extends NotFoundException {

    public OrderItemNotFoundException(Long id) {
        super("Unidad no encontrada: " + id);
    }
}
