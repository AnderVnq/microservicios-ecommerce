package com.example.ms_auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUsuarioRepository extends JpaRepository<UsuarioJpaEntity, Long> {

    boolean existsByKeycloakUserId(String keycloakUserId);

    @Query("""
            SELECT COUNT(u) > 0 FROM UsuarioJpaEntity u
            JOIN u.roles r JOIN r.permisos p
            WHERE u.keycloakUserId = :subject AND u.activo = true
            AND p.recurso = :recurso AND p.accion = :accion
            """)
    boolean tienePermiso(@Param("subject") String subject, @Param("recurso") String recurso, @Param("accion") String accion);
}
