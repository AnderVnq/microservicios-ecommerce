package com.example.msproducts.domain.exception;

public class MarketSkuNotFoundException extends NotFoundException {

    public MarketSkuNotFoundException(Long variantId, Long id) {
        super("La equivalencia %d no existe en la variacion %d".formatted(id, variantId));
    }
}
