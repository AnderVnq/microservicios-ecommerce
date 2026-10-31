package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.application.port.in.SkuResolution;

public record SkuResolutionResponse(Long marketId, String marketSku, String sku) {

    public static SkuResolutionResponse from(SkuResolution r) {
        return new SkuResolutionResponse(r.marketId(), r.marketSku(), r.sku());
    }
}
