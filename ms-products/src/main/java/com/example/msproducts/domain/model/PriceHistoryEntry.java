package com.example.msproducts.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Fila del historial de precios (la llenan triggers de la base). */
public record PriceHistoryEntry(Long id, Long variantId, BigDecimal price, String currency, String sourceUrl,
                                Instant recordedAt) {
}
