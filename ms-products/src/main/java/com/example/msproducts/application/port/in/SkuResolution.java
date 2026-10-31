package com.example.msproducts.application.port.in;

/** Resultado por entrada: el market y SKU tal como llegaron, y el SKU interno (null si no existe). */
public record SkuResolution(Long marketId, String marketSku, String sku) {
}
