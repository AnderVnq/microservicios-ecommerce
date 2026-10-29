package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;

import java.time.Instant;

public record HistoryEntryResponse(Long id, Long orderItemId, HistoryEventType eventType, String fromValue,
                                   String toValue, String performedBy, Instant occurredAt) {

    public static HistoryEntryResponse from(OrderItemHistoryEntry e) {
        return new HistoryEntryResponse(e.id(), e.orderItemId(), e.eventType(), e.fromValue(), e.toValue(),
                e.performedBy(), e.occurredAt());
    }
}
