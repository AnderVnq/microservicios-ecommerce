package com.example.msproducts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataProductRepository extends JpaRepository<ProductJpaEntity, Long> {
}
