package com.example.msclientes.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SpringDataFamiliarRepository extends JpaRepository<FamiliarJpaEntity, Long> {

    List<FamiliarJpaEntity> findByClienteId(Long clienteId);

    Optional<FamiliarJpaEntity> findByIdAndClienteId(Long id, Long clienteId);
}
