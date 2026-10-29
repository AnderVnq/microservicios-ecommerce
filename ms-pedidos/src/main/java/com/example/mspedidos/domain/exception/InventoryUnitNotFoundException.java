package com.example.mspedidos.domain.exception;

public class InventoryUnitNotFoundException extends NotFoundException {

    public InventoryUnitNotFoundException(Long id) {
        super("Unidad de inventario no encontrada: " + id);
    }
}
