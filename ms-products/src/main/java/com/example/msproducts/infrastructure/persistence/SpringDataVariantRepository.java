package com.example.msproducts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SpringDataVariantRepository extends JpaRepository<VariantJpaEntity, Long> {

    // La collation de la columna compara sin distinguir mayusculas
    Optional<VariantJpaEntity> findBySku(String sku);

    List<VariantJpaEntity> findBySkuIn(Collection<String> skus);

    List<VariantJpaEntity> findByProductIdInOrderByIdAsc(Collection<Long> productIds);

    Optional<VariantJpaEntity> findFirstByProductIdAndMainTrue(Long productId);
}
