package com.example.msmarkets.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMarketRepository extends JpaRepository<MarketJpaEntity, Long> {

    boolean existsByMarcaAndTipo(String marca, String tipo);
}
