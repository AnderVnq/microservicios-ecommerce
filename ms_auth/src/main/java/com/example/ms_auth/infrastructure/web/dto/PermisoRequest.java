package com.example.ms_auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PermisoRequest(
        @NotBlank String recurso,
        @NotBlank String accion,
        String descripcion
) {
}
