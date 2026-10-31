package com.example.msproducts.infrastructure.persistence;

import com.example.msproducts.application.port.out.CategoryRepositoryPort;
import com.example.msproducts.domain.model.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class CategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final SpringDataCategoryRepository jpaRepository;

    @Override
    public Optional<Category> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Category> findByParentAndName(Long parentId, String name) {
        return jpaRepository.findByParentIdAndName(parentId, name).map(this::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return jpaRepository.findAllByOrderByLevelAscNameAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public Category save(Category category) {
        return toDomain(jpaRepository.saveAndFlush(toEntity(category)));
    }

    private Category toDomain(CategoryJpaEntity e) {
        return Category.builder()
                .id(e.getId())
                .name(e.getName())
                .parentId(e.getParentId())
                .level(e.getLevel())
                .active(e.isActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private CategoryJpaEntity toEntity(Category c) {
        return CategoryJpaEntity.builder()
                .id(c.getId())
                .name(c.getName())
                .parentId(c.getParentId())
                .level((byte) c.getLevel())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
