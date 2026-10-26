package com.example.msclientes.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataReservaImportacionRepository extends JpaRepository<ReservaImportacionJpaEntity, Long> {

    Optional<ReservaImportacionJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
