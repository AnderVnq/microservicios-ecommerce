package com.example.mspedidos.domain.exception;

public class ApprovalForbiddenException extends RuntimeException {

    public ApprovalForbiddenException(String message) {
        super(message);
    }
}
