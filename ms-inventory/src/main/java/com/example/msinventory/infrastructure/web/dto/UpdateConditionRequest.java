package com.example.msinventory.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateConditionRequest(@NotBlank String name, @NotNull Boolean active) {
}
