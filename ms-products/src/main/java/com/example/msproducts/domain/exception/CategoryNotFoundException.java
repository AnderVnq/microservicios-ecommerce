package com.example.msproducts.domain.exception;

public class CategoryNotFoundException extends NotFoundException {

    public CategoryNotFoundException(Long id) {
        super("La categoria %d no existe".formatted(id));
    }

    public CategoryNotFoundException(String name) {
        super("La categoria '%s' no existe".formatted(name));
    }
}
