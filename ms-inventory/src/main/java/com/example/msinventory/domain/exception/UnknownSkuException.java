package com.example.msinventory.domain.exception;

public class UnknownSkuException extends RuntimeException {

    public UnknownSkuException(String message) {
        super(message);
    }
}
