package com.example.msmarkets.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarketRequest(

        @NotBlank
        @Size(max = 10)
        String code,

        @NotBlank
        String name,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency
) {
}
