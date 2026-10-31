package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.domain.model.DimensionUnit;
import com.example.msproducts.domain.model.Dimensions;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DimensionsDto(@NotNull BigDecimal length, @NotNull BigDecimal width, @NotNull BigDecimal height,
                            @NotBlank String unit) {

    public Dimensions toDomain() {
        return new Dimensions(length, width, height, DimensionUnit.parse(unit));
    }
}
