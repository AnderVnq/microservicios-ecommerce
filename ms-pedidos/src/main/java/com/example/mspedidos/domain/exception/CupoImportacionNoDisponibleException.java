package com.example.mspedidos.domain.exception;

/** El cliente titular (o su grupo familiar) ya consumio sus 3 importaciones disponibles. */
public class CupoImportacionNoDisponibleException extends RuntimeException {

    public CupoImportacionNoDisponibleException(Long clienteId) {
        super("El cliente %d no tiene cupo de importaciones disponible".formatted(clienteId));
    }
}
