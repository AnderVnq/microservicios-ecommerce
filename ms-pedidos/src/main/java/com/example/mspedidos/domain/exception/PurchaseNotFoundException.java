package com.example.mspedidos.domain.exception;

public class PurchaseNotFoundException extends NotFoundException {

    public PurchaseNotFoundException(Long id) {
        super("Compra no encontrada: " + id);
    }
}
