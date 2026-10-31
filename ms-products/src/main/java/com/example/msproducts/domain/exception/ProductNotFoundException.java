package com.example.msproducts.domain.exception;

public class ProductNotFoundException extends NotFoundException {

    public ProductNotFoundException(Long id) {
        super("El producto %d no existe".formatted(id));
    }
}
