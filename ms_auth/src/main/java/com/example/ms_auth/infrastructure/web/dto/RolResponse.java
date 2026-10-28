package com.example.ms_auth.infrastructure.web.dto;

import com.example.ms_auth.domain.model.Rol;

import java.util.Set;

public record RolResponse(
        Long id,
        String nombre,
        String descripcion,
        Set<Long> permisoIds
) {
    public static RolResponse from(Rol r) {
        return new RolResponse(r.getId(), r.getNombre(), r.getDescripcion(), r.getPermisoIds());
    }
}
