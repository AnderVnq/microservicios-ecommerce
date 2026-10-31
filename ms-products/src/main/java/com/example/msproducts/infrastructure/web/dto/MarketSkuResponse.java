package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.domain.model.MarketSku;

public record MarketSkuResponse(Long id, Long variantId, Long marketId, String marketSku) {

    public static MarketSkuResponse from(MarketSku m) {
        return new MarketSkuResponse(m.id(), m.variantId(), m.marketId(), m.marketSku());
    }
}
