package com.example.msproducts.domain.model;

import java.time.Instant;

/** Equivalencia: el SKU con que un market conoce a una variacion. */
public record MarketSku(Long id, Long variantId, Long marketId, String marketSku, String createdBy, Instant createdAt) {
}
