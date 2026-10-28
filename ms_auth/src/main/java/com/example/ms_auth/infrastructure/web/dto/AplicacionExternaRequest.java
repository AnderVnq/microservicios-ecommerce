package com.example.ms_auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AplicacionExternaRequest(
        @NotBlank String keycloakClientId,
        @NotBlank String nombre,
        String empresa
) {
}
