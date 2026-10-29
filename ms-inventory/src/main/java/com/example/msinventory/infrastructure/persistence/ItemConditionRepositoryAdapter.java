package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.application.port.out.ItemConditionRepositoryPort;
import com.example.msinventory.domain.model.ItemCondition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class ItemConditionRepositoryAdapter implements ItemConditionRepositoryPort {

    private final SpringDataItemConditionRepository repository;

    ItemConditionRepositoryAdapter(SpringDataItemConditionRepository repository) {
        this.repository = repository;
    }

    @Override
    public ItemCondition save(ItemCondition condition) {
        return toDomain(repository.save(new ItemConditionJpaEntity(
                condition.getCode(), condition.getName(), condition.isActive())));
    }

    @Override
    public Optional<ItemCondition> findByCode(String code) {
        return repository.findById(code).map(this::toDomain);
    }

    @Override
    public List<ItemCondition> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    private ItemCondition toDomain(ItemConditionJpaEntity e) {
        return new ItemCondition(e.getCode(), e.getName(), e.isActive());
    }
}
