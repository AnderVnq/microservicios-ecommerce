package com.example.msclientes.infrastructure.web.dto;

import com.example.msclientes.domain.model.Parentesco;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FamiliarRequest(
        @NotBlank String nombres,
        @NotBlank String apellidos,
        @NotBlank String tipoDocumento,
        @NotBlank String numeroDocumento,
        @NotNull Parentesco parentesco
) {
}
