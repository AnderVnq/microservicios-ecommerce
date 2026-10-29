package com.example.mspedidos.domain.exception;

public class PersonNotFoundException extends NotFoundException {

    public PersonNotFoundException(String detail) {
        super("Persona no encontrada: " + detail);
    }
}
