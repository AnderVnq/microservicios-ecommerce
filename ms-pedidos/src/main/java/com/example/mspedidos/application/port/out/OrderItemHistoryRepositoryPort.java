package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.OrderItemHistoryEntry;

import java.util.List;

public interface OrderItemHistoryRepositoryPort {

    void append(OrderItemHistoryEntry entry);

    /** Ordenado por occurredAt e id. */
    List<OrderItemHistoryEntry> findByOrderItemId(Long orderItemId);
}
