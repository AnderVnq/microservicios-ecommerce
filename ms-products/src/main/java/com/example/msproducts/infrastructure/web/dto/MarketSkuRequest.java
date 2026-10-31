package com.example.msproducts.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public record MarketSkuRequest(@NotNull Long marketId, @NotBlank @Size(max = 100) String marketSku) {
}
