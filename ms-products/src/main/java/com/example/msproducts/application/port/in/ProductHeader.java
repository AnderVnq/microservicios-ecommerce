package com.example.msproducts.application.port.in;

import com.example.msproducts.domain.model.ProductStatus;

/** Campos del producto padre. status null: ACTIVE al crear, se conserva al editar. */
public record ProductHeader(String name, String brand, String description, Long categoryId, String hsCode,
                            String imageUrl, ProductStatus status) {
}
