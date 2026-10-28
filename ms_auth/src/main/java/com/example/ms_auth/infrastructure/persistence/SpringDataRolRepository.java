package com.example.ms_auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataRolRepository extends JpaRepository<RolJpaEntity, Long> {

    boolean existsByNombre(String nombre);

    List<RolJpaEntity> findAllByIdIn(List<Long> ids);
}
