package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.AllocationUndoRequest;
import com.example.mspedidos.domain.model.UndoRequestStatus;

import java.time.Instant;

public record AllocationUndoRequestResponse(
        Long id,
        Long orderItemId,
        Long inventoryUnitId,
        String reason,
        UndoRequestStatus status,
        String requestedBy,
        String decidedBy,
        String decisionNote,
        Instant requestedAt,
        Instant decidedAt
) {
    public static AllocationUndoRequestResponse from(AllocationUndoRequest r) {
        return new AllocationUndoRequestResponse(r.getId(), r.getOrderItemId(), r.getInventoryUnitId(), r.getReason(),
                r.getStatus(), r.getRequestedBy(), r.getDecidedBy(), r.getDecisionNote(), r.getRequestedAt(),
                r.getDecidedAt());
    }
}
