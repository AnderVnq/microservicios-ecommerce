package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record NotReceivedRequest(@Min(1) @Max(500) int quantity, @NotBlank String reason) {
}
