package com.example.msinventory.domain.exception;

public class AllocationConflictException extends BusinessRuleException {

    public AllocationConflictException(String message) {
        super(message);
    }
}
