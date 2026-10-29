package com.example.mspedidos.domain.exception;

/**
 * ms-inventory rechazo de forma definitiva (4xx) una operacion de sincronizacion de compras; reintentar con el mismo
 * cuerpo no cambiaria el resultado. Lleva el {@code code} del ApiError remoto.
 */
public class InventorySyncRejectedException extends RuntimeException {

    private final String code;

    public InventorySyncRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
