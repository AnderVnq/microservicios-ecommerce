package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;

import java.time.Instant;

public record StatusChangeRequestResponse(
        Long id,
        StatusChangeRequestType type,
        Long orderItemId,
        Long purchaseId,
        Integer quantity,
        Long inventoryUnitId,
        String reason,
        StatusChangeRequestStatus status,
        String requestedBy,
        String decidedBy,
        String decisionNote,
        Instant requestedAt,
        Instant decidedAt
) {
    public static StatusChangeRequestResponse from(StatusChangeRequest r) {
        return new StatusChangeRequestResponse(r.getId(), r.getType(), r.getOrderItemId(), r.getPurchaseId(),
                r.getQuantity(), r.getInventoryUnitId(), r.getReason(), r.getStatus(), r.getRequestedBy(),
                r.getDecidedBy(), r.getDecisionNote(), r.getRequestedAt(), r.getDecidedAt());
    }
}
