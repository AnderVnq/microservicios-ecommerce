package com.example.msproducts.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Producto padre del catalogo; los datos vendibles viven en sus variaciones. */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Product {

    private Long id;
    private String name;
    private String brand;
    private String description;
    private Long categoryId;
    private String hsCode;
    private String imageUrl;
    private ProductStatus status;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
