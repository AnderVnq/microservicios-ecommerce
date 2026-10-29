package com.example.mspedidos.domain.model;

/** Documento del importador confirmado; es la clave del cupo. */
public record ImporterDocument(String type, String number) {

    public ImporterDocument {
        if (type == null || type.isBlank() || number == null || number.isBlank()) {
            throw new IllegalArgumentException("El tipo y numero de documento del importador son obligatorios");
        }
    }
}
