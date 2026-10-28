package com.example.msmarkets.infrastructure.web.dto;

import com.example.msmarkets.domain.model.Market;

import java.time.Instant;

public record MarketResponse(
        Long id,
        String marca,
        String tipo,
        Integer slaEntregaHoras,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static MarketResponse from(Market market) {
        return new MarketResponse(
                market.getId(),
                market.getMarca(),
                market.getTipo(),
                market.getSlaEntregaHoras(),
                market.isActive(),
                market.getCreatedAt(),
                market.getUpdatedAt()
        );
    }
}
