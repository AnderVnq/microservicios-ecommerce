package com.example.mspedidos.application.service;

import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestType;

/**
 * Efecto de un tipo de solicitud de cambio de estado. {@link StatusChangeRequestService} descubre los beans por
 * {@link #type()} y llama {@link #lockAndValidate} y {@link #apply} dentro de su transaccion, y {@link #afterCommit}
 * despues del commit.
 */
public interface StatusChangeApplier {

    StatusChangeRequestType type();

    /**
     * Dentro de la transaccion del llamador: toma los locks de esta solicitud (en el orden global, antes del lock de
     * la fila de la solicitud) y valida; lanza subclases de {@code BusinessRuleException} si no procede.
     */
    void lockAndValidate(StatusChangeRequest req);

    /** En la misma transaccion, despues de {@link #lockAndValidate}: aplica el cambio y escribe el historial. */
    void apply(StatusChangeRequest req, String actor);

    /** Despues del commit, fuera de toda transaccion: efectos remotos. No lanza por fallos remotos. */
    void afterCommit(StatusChangeRequest req, String actor);
}
