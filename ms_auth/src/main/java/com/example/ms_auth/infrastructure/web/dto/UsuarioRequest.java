package com.example.ms_auth.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UsuarioRequest(
        @NotBlank String keycloakUserId,
        @NotBlank String nombres,
        @NotBlank String apellidos,
        @Email String email
) {
}
