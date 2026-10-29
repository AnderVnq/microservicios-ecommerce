package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateItemRequest(
        @NotBlank String sku,
        String externalItemId,
        @NotNull @DecimalMin("0") BigDecimal unitPrice
) {
}
