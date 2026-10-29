package com.example.mspedidos.domain.exception;

public class PurchaseQuantityExceededException extends RuntimeException {

    public PurchaseQuantityExceededException(String message) {
        super(message);
    }
}
