package com.example.msproducts.domain.exception;

public class SkuAlreadyExistsException extends BusinessRuleException {

    public SkuAlreadyExistsException(String sku) {
        super("El SKU '%s' ya existe".formatted(sku));
    }
}
