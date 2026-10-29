package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.OrderItemHistoryRepositoryPort;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class OrderItemHistoryRepositoryAdapter implements OrderItemHistoryRepositoryPort {

    private final SpringDataOrderItemHistoryRepository jpaRepository;

    @Override
    public void append(OrderItemHistoryEntry entry) {
        jpaRepository.save(OrderItemHistoryJpaEntity.builder()
                .orderItemId(entry.orderItemId())
                .eventType(entry.eventType())
                .fromValue(entry.fromValue())
                .toValue(entry.toValue())
                .performedBy(entry.performedBy())
                .occurredAt(entry.occurredAt())
                .build());
    }

    @Override
    public List<OrderItemHistoryEntry> findByOrderItemId(Long orderItemId) {
        return jpaRepository.findByOrderItemIdOrderByOccurredAtAscIdAsc(orderItemId).stream()
                .map(e -> new OrderItemHistoryEntry(e.getId(), e.getOrderItemId(), e.getEventType(),
                        e.getFromValue(), e.getToValue(), e.getPerformedBy(), e.getOccurredAt()))
                .toList();
    }
}
