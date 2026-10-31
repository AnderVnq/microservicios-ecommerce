package com.example.msproducts.domain.exception;

public class CategoryDepthExceededException extends BusinessRuleException {

    public static final int MAX_LEVEL = 3;

    public CategoryDepthExceededException() {
        super("Las categorias admiten maximo %d niveles".formatted(MAX_LEVEL));
    }
}
