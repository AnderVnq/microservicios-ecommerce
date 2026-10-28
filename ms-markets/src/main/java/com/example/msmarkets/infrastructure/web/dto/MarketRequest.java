package com.example.msmarkets.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record MarketRequest(

        /** Ej: Falabella, Ripley. Se repite entre varios Market del mismo tipo distinto. */
        @NotBlank
        String marca,

        /** Ej: Express, USA, 24h, China. */
        @NotBlank
        String tipo,

        @Positive
        Integer slaEntregaHoras
) {
}
