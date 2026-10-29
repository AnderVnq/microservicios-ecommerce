package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;

import java.util.List;

/**
 * Solicitudes de cambio de estado (spec 8.4): quien tiene {@code status-change/apply-direct} las aplica en el acto;
 * el resto queda {@code PENDING} hasta que alguien con {@code status-change/approve} (no el solicitante) decide.
 */
public interface StatusChangeRequestUseCase {

    /** Deshacer la cobertura de inventario de una unidad (no aplica a unidades cubiertas por compra). */
    StatusChangeRequest requestAllocationUndo(Long orderId, Long itemId, String reason);

    /**
     * Registra un borrador {@code PENDING} (sin id) creado por {@code draft.requestedBy}: lo aplica directamente si
     * el solicitante tiene permiso o lo deja pendiente.
     */
    StatusChangeRequest submit(StatusChangeRequest draft);

    /** {@code null} no filtra; mas recientes primero. */
    List<StatusChangeRequest> search(StatusChangeRequestStatus status, StatusChangeRequestType type);

    StatusChangeRequest approve(Long id, String note);

    /** La nota es obligatoria; no cambia nada mas que la solicitud. */
    StatusChangeRequest reject(Long id, String note);
}
