package com.example.mspedidos.domain.model;

/** Persona (titular o familiar) tal como la devuelve ms-clientes. */
public record Person(Long id, String fullName, ImporterDocument document, boolean active) {
}
