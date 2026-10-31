package com.example.mspedidos.domain.exception;

public class ProductsUnavailableException extends RuntimeException {

    public ProductsUnavailableException(String message) {
        super(message);
    }
}
