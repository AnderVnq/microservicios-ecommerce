package com.example.mspedidos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SpringDataPurchaseArrivalRepository extends JpaRepository<PurchaseArrivalJpaEntity, Long> {

    Optional<PurchaseArrivalJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<PurchaseArrivalJpaEntity> findByPurchaseIdOrderByIdAsc(Long purchaseId);
}
