package com.example.msproducts.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Categoria del catalogo: arbol de hasta 3 niveles (level 1 = raiz). */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Category {

    private Long id;
    private String name;
    private Long parentId;
    private int level;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
