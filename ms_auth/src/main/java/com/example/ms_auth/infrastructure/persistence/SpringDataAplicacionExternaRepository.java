package com.example.ms_auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAplicacionExternaRepository extends JpaRepository<AplicacionExternaJpaEntity, Long> {

    boolean existsByKeycloakClientId(String keycloakClientId);

    @Query("""
            SELECT COUNT(a) > 0 FROM AplicacionExternaJpaEntity a
            JOIN a.permisos p
            WHERE a.keycloakClientId = :subject AND a.activa = true
            AND p.recurso = :recurso AND p.accion = :accion
            """)
    boolean tienePermiso(@Param("subject") String subject, @Param("recurso") String recurso, @Param("accion") String accion);
}
