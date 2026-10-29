package com.example.mspedidos.domain.exception;

public class SelfApprovalNotAllowedException extends BusinessRuleException {

    public SelfApprovalNotAllowedException(String message) {
        super(message);
    }
}
