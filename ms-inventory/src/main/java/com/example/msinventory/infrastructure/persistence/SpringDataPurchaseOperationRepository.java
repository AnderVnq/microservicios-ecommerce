package com.example.msinventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataPurchaseOperationRepository extends JpaRepository<PurchaseOperationJpaEntity, Long> {

    Optional<PurchaseOperationJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
