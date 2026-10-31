package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code sku} y {@code marketSku} son opcionales: si no vienen se conserva el valor actual. */
public record UpdateItemRequest(
        @Size(max = 255) String sku,
        @Size(max = 255) String marketSku,
        String externalItemId,
        @NotNull @DecimalMin("0") BigDecimal unitPrice
) {
}
