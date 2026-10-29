package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.ItemConditionUseCase;
import com.example.msinventory.application.port.out.ItemConditionRepositoryPort;
import com.example.msinventory.domain.exception.ConditionAlreadyExistsException;
import com.example.msinventory.domain.exception.ConditionNotFoundException;
import com.example.msinventory.domain.model.ItemCondition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ItemConditionService implements ItemConditionUseCase {

    private final ItemConditionRepositoryPort conditions;

    public ItemConditionService(ItemConditionRepositoryPort conditions) {
        this.conditions = conditions;
    }

    @Override
    public List<ItemCondition> findAll() {
        return conditions.findAll();
    }

    @Override
    public ItemCondition create(String code, String name) {
        String normalized = normalize(code);
        requireName(name);
        if (conditions.findByCode(normalized).isPresent()) {
            throw new ConditionAlreadyExistsException("La condicion " + normalized + " ya existe");
        }
        return conditions.save(new ItemCondition(normalized, name.trim(), true));
    }

    @Override
    public ItemCondition update(String code, String name, boolean active) {
        String normalized = normalize(code);
        requireName(name);
        conditions.findByCode(normalized)
                .orElseThrow(() -> new ConditionNotFoundException("Condicion no encontrada: " + normalized));
        return conditions.save(new ItemCondition(normalized, name.trim(), active));
    }

    private static String normalize(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El codigo de la condicion es obligatorio");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la condicion es obligatorio");
        }
    }
}
