package com.example.msmarkets.domain.exception;

public class MarketNotFoundException extends RuntimeException {

    public MarketNotFoundException(Long id) {
        super("Market %d no existe".formatted(id));
    }
}
