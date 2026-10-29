package com.example.mspedidos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataOrderItemHistoryRepository extends JpaRepository<OrderItemHistoryJpaEntity, Long> {

    List<OrderItemHistoryJpaEntity> findByOrderItemIdOrderByOccurredAtAscIdAsc(Long orderItemId);
}
