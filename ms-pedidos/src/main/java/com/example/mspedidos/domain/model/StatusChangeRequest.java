package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.SelfApprovalNotAllowedException;
import com.example.mspedidos.domain.exception.StatusChangeRequestAlreadyDecidedException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Solicitud (con aprobacion de un segundo actor) para deshacer la cobertura de una unidad desde inventario. */
@Getter
@Builder
@AllArgsConstructor
public class StatusChangeRequest {

    private Long id;
    private StatusChangeRequestType type;
    private Long orderItemId;
    private Long purchaseId;
    private Integer quantity;
    private Long inventoryUnitId;
    private String reason;
    private StatusChangeRequestStatus status;
    private String requestedBy;
    private String decidedBy;
    private String decisionNote;
    private Instant requestedAt;
    private Instant decidedAt;
    /** {@code Idempotency-Key} con la que se creo (solo "no recibido"); null en los demas tipos. */
    private String idempotencyKey;
    private String requestHash;

    public static StatusChangeRequest openAllocationUndo(Long orderItemId, Long inventoryUnitId, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("El solicitante es obligatorio");
        }
        return StatusChangeRequest.builder()
                .type(StatusChangeRequestType.ALLOCATION_UNDO)
                .orderItemId(orderItemId)
                .inventoryUnitId(inventoryUnitId)
                .reason(reason)
                .status(StatusChangeRequestStatus.PENDING)
                .requestedBy(actor)
                .requestedAt(Instant.now())
                .build();
    }

    /** Solicitud de "no recibido" de una cantidad de una compra. */
    public static StatusChangeRequest openNotReceived(Long purchaseId, Integer quantity, String reason, String actor) {
        return openNotReceived(purchaseId, quantity, reason, actor, null, null);
    }

    /** Como {@link #openNotReceived(Long, Integer, String, String)} con la clave de idempotencia y el hash. */
    public static StatusChangeRequest openNotReceived(Long purchaseId, Integer quantity, String reason, String actor,
                                                      String idempotencyKey, String requestHash) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("La cantidad debe ser al menos 1");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("El solicitante es obligatorio");
        }
        return StatusChangeRequest.builder()
                .type(StatusChangeRequestType.PURCHASE_NOT_RECEIVED)
                .purchaseId(purchaseId)
                .quantity(quantity)
                .reason(reason)
                .status(StatusChangeRequestStatus.PENDING)
                .requestedBy(actor)
                .requestedAt(Instant.now())
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .build();
    }

    /** Aplicada de inmediato por un actor con permiso de aplicacion directa (sin segundo aprobador). */
    public void markAppliedDirectly(String actor) {
        ensurePending();
        decide(StatusChangeRequestStatus.APPLIED_DIRECTLY, actor, null);
    }

    public void approve(String approver, String note) {
        ensurePending();
        if (requestedBy != null && requestedBy.equals(approver)) {
            throw new SelfApprovalNotAllowedException("El solicitante no puede aprobar su propia solicitud");
        }
        decide(StatusChangeRequestStatus.APPROVED, approver, note);
    }

    public void reject(String approver, String note) {
        ensurePending();
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("La nota es obligatoria para rechazar");
        }
        decide(StatusChangeRequestStatus.REJECTED, approver, note);
    }

    private void ensurePending() {
        if (status != StatusChangeRequestStatus.PENDING) {
            throw new StatusChangeRequestAlreadyDecidedException("La solicitud ya fue resuelta: " + status);
        }
    }

    private void decide(StatusChangeRequestStatus result, String approver, String note) {
        this.status = result;
        this.decidedBy = approver;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }
}
