package com.example.msinventory.application.port.out;

import com.example.msinventory.domain.model.ItemCondition;

import java.util.List;
import java.util.Optional;

public interface ItemConditionRepositoryPort {

    ItemCondition save(ItemCondition condition);

    Optional<ItemCondition> findByCode(String code);

    List<ItemCondition> findAll();
}
