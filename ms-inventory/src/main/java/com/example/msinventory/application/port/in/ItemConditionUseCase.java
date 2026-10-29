package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.ItemCondition;

import java.util.List;

public interface ItemConditionUseCase {

    List<ItemCondition> findAll();

    ItemCondition create(String code, String name);

    ItemCondition update(String code, String name, boolean active);
}
