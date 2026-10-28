package com.example.ms_auth.infrastructure.web.dto;

import com.example.ms_auth.domain.model.AplicacionExterna;

import java.time.Instant;
import java.util.Set;

public record AplicacionExternaResponse(
        Long id,
        String keycloakClientId,
        String nombre,
        String empresa,
        boolean activa,
        Set<Long> permisoIds,
        Instant createdAt,
        Instant updatedAt
) {
    public static AplicacionExternaResponse from(AplicacionExterna a) {
        return new AplicacionExternaResponse(a.getId(), a.getKeycloakClientId(), a.getNombre(), a.getEmpresa(),
                a.isActiva(), a.getPermisoIds(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
