package com.example.msproducts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataPriceHistoryRepository extends JpaRepository<PriceHistoryJpaEntity, Long> {

    List<PriceHistoryJpaEntity> findByVariantIdOrderByRecordedAtAscIdAsc(Long variantId);
}
