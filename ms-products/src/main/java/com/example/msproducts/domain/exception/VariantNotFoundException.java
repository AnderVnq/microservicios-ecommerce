package com.example.msproducts.domain.exception;

public class VariantNotFoundException extends NotFoundException {

    public VariantNotFoundException(String sku) {
        super("La variacion con SKU '%s' no existe".formatted(sku));
    }

    public VariantNotFoundException(Long id) {
        super("La variacion %d no existe".formatted(id));
    }
}
