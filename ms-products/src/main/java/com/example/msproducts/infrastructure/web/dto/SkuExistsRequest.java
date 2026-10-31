package com.example.msproducts.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SkuExistsRequest(@NotNull @Size(max = 500) List<@NotBlank String> skus) {
}
