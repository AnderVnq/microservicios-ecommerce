package com.example.msclientes.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

public record ReservaCupoRequest(
        @NotNull Long pedidoId
) {
}
