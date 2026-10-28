package com.example.ms_auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RolRequest(
        @NotBlank String nombre,
        String descripcion
) {
}
