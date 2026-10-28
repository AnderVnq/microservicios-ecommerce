package com.example.ms_auth.infrastructure.web.dto;

import com.example.ms_auth.domain.model.Permiso;

public record PermisoResponse(
        Long id,
        String recurso,
        String accion,
        String descripcion
) {
    public static PermisoResponse from(Permiso p) {
        return new PermisoResponse(p.getId(), p.getRecurso(), p.getAccion(), p.getDescripcion());
    }
}
