package com.example.msinventory.domain.exception;

public class ProductsUnavailableException extends RuntimeException {

    public ProductsUnavailableException(String message) {
        super(message);
    }
}
