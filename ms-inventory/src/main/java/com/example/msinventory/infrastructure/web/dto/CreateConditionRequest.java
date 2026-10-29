package com.example.msinventory.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateConditionRequest(@NotBlank String code, @NotBlank String name) {
}
