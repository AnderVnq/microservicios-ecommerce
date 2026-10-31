package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.domain.model.Category;

public record CategoryResponse(Long id, String name, Long parentId, int level, boolean active) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getParentId(), c.getLevel(), c.isActive());
    }
}
