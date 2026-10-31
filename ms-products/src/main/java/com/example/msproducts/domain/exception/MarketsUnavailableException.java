package com.example.msproducts.domain.exception;

/** ms-markets no responde o falla (HTTP 503). */
public class MarketsUnavailableException extends RuntimeException {

    public MarketsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
