package com.example.msinventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ItemCondition {

    private String code;
    private String name;
    private boolean active;
}
