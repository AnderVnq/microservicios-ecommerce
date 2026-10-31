package com.example.msproducts.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ResolveSkusRequest(@NotNull @Size(max = 500) List<@NotNull @Valid Item> marketSkus) {

    public record Item(@NotNull Long marketId, @NotBlank String marketSku) {
    }
}
