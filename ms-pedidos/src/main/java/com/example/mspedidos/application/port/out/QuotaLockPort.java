package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.ImporterDocument;

public interface QuotaLockPort {

    /**
     * Debe invocarse dentro de una transaccion activa. Bloquea hasta obtener un lock pesimista
     * sobre la fila (documento, anio), creandola si no existe.
     */
    void lock(ImporterDocument document, int year);
}
