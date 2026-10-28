package com.example.msmarkets.domain.exception;

public class MarketAlreadyExistsException extends RuntimeException {

    public MarketAlreadyExistsException(String marca, String tipo) {
        super("Ya existe un market %s - %s".formatted(marca, tipo));
    }
}
