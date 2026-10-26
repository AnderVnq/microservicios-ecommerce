package com.example.msclientes.domain.exception;

public class FamiliarNotFoundException extends RuntimeException {

    public FamiliarNotFoundException(Long clienteId, Long familiarId) {
        super("Familiar %d no existe para el cliente %d".formatted(familiarId, clienteId));
    }
}
