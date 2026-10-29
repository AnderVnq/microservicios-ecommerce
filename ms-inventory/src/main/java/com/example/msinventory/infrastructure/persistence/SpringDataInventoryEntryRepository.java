package com.example.msinventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataInventoryEntryRepository extends JpaRepository<InventoryEntryJpaEntity, Long> {

    Optional<InventoryEntryJpaEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<InventoryEntryJpaEntity> findByPurchaseId(Long purchaseId);
}
