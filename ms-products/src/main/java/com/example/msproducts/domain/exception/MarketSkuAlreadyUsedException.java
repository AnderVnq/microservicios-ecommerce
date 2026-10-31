package com.example.msproducts.domain.exception;

public class MarketSkuAlreadyUsedException extends BusinessRuleException {

    public MarketSkuAlreadyUsedException(Long marketId, String marketSku) {
        super("El SKU '%s' ya esta asignado en el market %d".formatted(marketSku, marketId));
    }
}
