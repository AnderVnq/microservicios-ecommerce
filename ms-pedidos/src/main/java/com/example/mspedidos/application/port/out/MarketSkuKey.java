package com.example.mspedidos.application.port.out;

/** SKU de market de un market concreto: la clave con la que se resuelve contra el catalogo. */
public record MarketSkuKey(Long marketId, String marketSku) {
}
