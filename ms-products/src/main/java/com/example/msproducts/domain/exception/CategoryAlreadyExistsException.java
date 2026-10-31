package com.example.msproducts.domain.exception;

public class CategoryAlreadyExistsException extends BusinessRuleException {

    public CategoryAlreadyExistsException(String name) {
        super("Ya existe una categoria '%s' en ese nivel".formatted(name));
    }
}
