package com.example.msproducts.domain.exception;

public class MainVariantRequiredException extends BusinessRuleException {

    public MainVariantRequiredException() {
        super("El producto debe conservar una variacion principal: marque otra como principal");
    }
}
