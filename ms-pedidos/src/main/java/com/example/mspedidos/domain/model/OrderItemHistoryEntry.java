package com.example.mspedidos.domain.model;

import java.time.Instant;

public record OrderItemHistoryEntry(Long id, Long orderItemId, HistoryEventType eventType,
                                    String fromValue, String toValue, String performedBy, Instant occurredAt) {
}
