package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.domain.model.PriceHistoryEntry;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceHistoryResponse(Long id, BigDecimal price, String currency, String sourceUrl, Instant recordedAt) {

    public static PriceHistoryResponse from(PriceHistoryEntry e) {
        return new PriceHistoryResponse(e.id(), e.price(), e.currency(), e.sourceUrl(), e.recordedAt());
    }
}
