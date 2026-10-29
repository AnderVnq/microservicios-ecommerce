package com.example.msinventory.infrastructure.web.dto;

import com.example.msinventory.domain.model.ItemCondition;

public record ConditionResponse(String code, String name, boolean active) {

    public static ConditionResponse from(ItemCondition c) {
        return new ConditionResponse(c.getCode(), c.getName(), c.isActive());
    }
}
