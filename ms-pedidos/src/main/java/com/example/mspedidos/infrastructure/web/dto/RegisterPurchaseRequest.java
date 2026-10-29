package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RegisterPurchaseRequest(
        @NotNull Long supplierId,
        @NotBlank String sku,
        @NotBlank String conditionCode,
        @Min(1) @Max(500) int quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitCost,
        @Pattern(regexp = "[A-Z]{3}") String costCurrency,
        @NotBlank String supplierPurchaseId,
        String invoiceNumber,
        @NotNull LocalDate expectedArrivalDate,
        List<@NotNull Long> orderItemIds) {
}
