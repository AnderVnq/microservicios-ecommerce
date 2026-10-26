package com.example.msclientes.domain.exception;

public class CupoImportacionAgotadoException extends RuntimeException {

    public CupoImportacionAgotadoException(Long clienteId) {
        super("Cliente %d no tiene cupo de importaciones disponible".formatted(clienteId));
    }
}
