package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.SelfApprovalNotAllowedException;
import com.example.mspedidos.domain.exception.UndoRequestAlreadyDecidedException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Solicitud (con aprobacion de un segundo actor) para deshacer la cobertura de una unidad desde inventario. */
@Getter
@Builder
@AllArgsConstructor
public class AllocationUndoRequest {

    private Long id;
    private Long orderItemId;
    private Long inventoryUnitId;
    private String reason;
    private UndoRequestStatus status;
    private String requestedBy;
    private String decidedBy;
    private String decisionNote;
    private Instant requestedAt;
    private Instant decidedAt;

    public static AllocationUndoRequest open(Long orderItemId, Long inventoryUnitId, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("El solicitante es obligatorio");
        }
        return AllocationUndoRequest.builder()
                .orderItemId(orderItemId)
                .inventoryUnitId(inventoryUnitId)
                .reason(reason)
                .status(UndoRequestStatus.PENDING)
                .requestedBy(actor)
                .requestedAt(Instant.now())
                .build();
    }

    public void approve(String approver, String note) {
        ensurePending();
        if (requestedBy != null && requestedBy.equals(approver)) {
            throw new SelfApprovalNotAllowedException("El solicitante no puede aprobar su propia solicitud");
        }
        decide(UndoRequestStatus.APPROVED, approver, note);
    }

    public void reject(String approver, String note) {
        ensurePending();
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("La nota es obligatoria para rechazar");
        }
        decide(UndoRequestStatus.REJECTED, approver, note);
    }

    private void ensurePending() {
        if (status != UndoRequestStatus.PENDING) {
            throw new UndoRequestAlreadyDecidedException("La solicitud ya fue resuelta: " + status);
        }
    }

    private void decide(UndoRequestStatus result, String approver, String note) {
        this.status = result;
        this.decidedBy = approver;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }
}
