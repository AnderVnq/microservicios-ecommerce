package com.example.msinventory.domain.exception;

public class PurchaseAlreadyRegisteredException extends BusinessRuleException {

    public PurchaseAlreadyRegisteredException(String message) {
        super(message);
    }
}
