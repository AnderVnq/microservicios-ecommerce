package com.example.msproducts.application.port.out;

/** Equivalencia leida con el SKU interno de su variacion (casing tal como esta guardado). */
public record ResolvedMarketSku(Long marketId, String marketSku, String variantSku) {
}
