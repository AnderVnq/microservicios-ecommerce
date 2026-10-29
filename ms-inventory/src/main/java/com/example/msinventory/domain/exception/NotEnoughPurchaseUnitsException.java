package com.example.msinventory.domain.exception;

public class NotEnoughPurchaseUnitsException extends BusinessRuleException {

    public NotEnoughPurchaseUnitsException(String message) {
        super(message);
    }
}
