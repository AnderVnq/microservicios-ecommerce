package com.example.msinventory.infrastructure.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ArrivalsRequest(@NotEmpty List<@NotNull Long> unitIds) {
}
