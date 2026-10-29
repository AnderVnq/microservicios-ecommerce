package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectUndoRequest(@NotBlank String note) {
}
