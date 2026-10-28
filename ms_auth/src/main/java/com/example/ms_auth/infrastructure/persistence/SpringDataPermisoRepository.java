package com.example.ms_auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataPermisoRepository extends JpaRepository<PermisoJpaEntity, Long> {

    boolean existsByRecursoAndAccion(String recurso, String accion);

    List<PermisoJpaEntity> findAllByIdIn(List<Long> ids);
}
