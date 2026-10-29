package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UndoRequestCreateRequest(@NotBlank String reason) {
}
