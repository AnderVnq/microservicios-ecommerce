package com.example.msinventory.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Cuerpo de llegadas y no recibidos; {@code reason} solo es obligatorio en no recibidos. */
public record PurchaseUnitsRequest(
        @NotNull @Min(1) @Max(500) Integer quantity,
        List<@NotNull Long> orderItemIds,
        String reason) {

    public PurchaseUnitsRequest {
        orderItemIds = orderItemIds == null ? List.of() : orderItemIds;
    }
}
