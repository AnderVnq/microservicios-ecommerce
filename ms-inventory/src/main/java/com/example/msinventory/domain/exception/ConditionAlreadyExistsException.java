package com.example.msinventory.domain.exception;

public class ConditionAlreadyExistsException extends BusinessRuleException {

    public ConditionAlreadyExistsException(String message) {
        super(message);
    }
}
