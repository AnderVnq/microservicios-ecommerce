package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.application.port.in.OrderLine;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderLineRequest(
        @NotBlank String marketSku,
        String externalItemId,
        @Min(1) int quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice
) {
    public OrderLine toLine() {
        return new OrderLine(marketSku == null ? null : marketSku.trim(), externalItemId, quantity, unitPrice);
    }
}
