package com.example.msmarkets.domain.exception;

public class MarketAlreadyExistsException extends RuntimeException {

    public MarketAlreadyExistsException(String code) {
        super("Ya existe un market con code " + code);
    }
}
