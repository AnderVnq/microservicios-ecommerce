package com.example.msproducts.infrastructure.web;

import com.example.msproducts.application.port.in.CategoryNode;
import com.example.msproducts.application.port.in.CategoryUseCase;
import com.example.msproducts.infrastructure.web.dto.CategoryRequest;
import com.example.msproducts.infrastructure.web.dto.CategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Adaptador driver: traduce HTTP al puerto de entrada CategoryUseCase. */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryUseCase categoryUseCase;

    @GetMapping
    public List<CategoryNode> tree() {
        return categoryUseCase.tree();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(categoryUseCase.create(request.name(), request.parentId()));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(categoryUseCase.update(id, request.name(), request.active()));
    }
}
