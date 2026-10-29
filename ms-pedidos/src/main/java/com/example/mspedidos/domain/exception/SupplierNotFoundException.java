package com.example.mspedidos.domain.exception;

public class SupplierNotFoundException extends NotFoundException {

    public SupplierNotFoundException(Long id) {
        super("Proveedor no encontrado: " + id);
    }
}
