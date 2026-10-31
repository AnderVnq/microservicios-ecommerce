package com.example.msproducts.application.port.out;

import com.example.msproducts.domain.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepositoryPort {

    Optional<Category> findById(Long id);

    /** Hermano con ese nombre (la base compara sin distinguir mayusculas); parentId null = raiz. */
    Optional<Category> findByParentAndName(Long parentId, String name);

    /** Todas, ordenadas por nivel y nombre. */
    List<Category> findAll();

    /** Guarda y fuerza el INSERT/UPDATE; lanza DataIntegrityViolationException si choca el unico. */
    Category save(Category category);
}
