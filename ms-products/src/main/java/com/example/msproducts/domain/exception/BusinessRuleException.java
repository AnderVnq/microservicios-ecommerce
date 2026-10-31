package com.example.msproducts.domain.exception;

/** Base de los conflictos de negocio (HTTP 409); el codigo sale del nombre de la subclase. */
public abstract class BusinessRuleException extends RuntimeException {

    protected BusinessRuleException(String message) {
        super(message);
    }
}
