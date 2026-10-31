package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.domain.model.Weight;
import com.example.msproducts.domain.model.WeightUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WeightDto(@NotNull BigDecimal value, @NotBlank String unit) {

    public Weight toDomain() {
        return new Weight(value, WeightUnit.parse(unit));
    }
}
