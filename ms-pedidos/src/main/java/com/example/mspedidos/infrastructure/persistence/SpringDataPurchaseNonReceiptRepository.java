package com.example.mspedidos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SpringDataPurchaseNonReceiptRepository extends JpaRepository<PurchaseNonReceiptJpaEntity, Long> {

    Optional<PurchaseNonReceiptJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<PurchaseNonReceiptJpaEntity> findByPurchaseIdOrderByIdAsc(Long purchaseId);
}
