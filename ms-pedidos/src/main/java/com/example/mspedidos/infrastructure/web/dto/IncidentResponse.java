package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;

import java.time.Instant;

public record IncidentResponse(Long id, Long orderItemId, IncidentType type, IncidentScope scope,
                               String description, IncidentStatus status, String openedBy, String resolvedBy,
                               Instant openedAt, Instant resolvedAt) {

    public static IncidentResponse from(ItemIncident i) {
        return new IncidentResponse(i.getId(), i.getOrderItemId(), i.getType(), i.getScope(), i.getDescription(),
                i.getStatus(), i.getOpenedBy(), i.getResolvedBy(), i.getOpenedAt(), i.getResolvedAt());
    }
}
