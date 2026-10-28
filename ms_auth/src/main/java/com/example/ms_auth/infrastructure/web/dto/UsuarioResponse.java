package com.example.ms_auth.infrastructure.web.dto;

import com.example.ms_auth.domain.model.Usuario;

import java.time.Instant;
import java.util.Set;

public record UsuarioResponse(
        Long id,
        String keycloakUserId,
        String nombres,
        String apellidos,
        String email,
        boolean activo,
        Set<Long> rolIds,
        Instant createdAt,
        Instant updatedAt
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getKeycloakUserId(), u.getNombres(), u.getApellidos(),
                u.getEmail(), u.isActivo(), u.getRolIds(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
