package com.example.msmarkets.infrastructure.web.dto;

import com.example.msmarkets.domain.model.Market;

import java.time.Instant;

public record MarketResponse(
        Long id,
        String code,
        String name,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static MarketResponse from(Market market) {
        return new MarketResponse(
                market.getId(),
                market.getCode(),
                market.getName(),
                market.getCurrency(),
                market.isActive(),
                market.getCreatedAt(),
                market.getUpdatedAt()
        );
    }
}
