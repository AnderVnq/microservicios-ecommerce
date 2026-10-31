package com.example.msproducts.domain.model;

import java.math.BigDecimal;

/** Peso tal como lo escribio el usuario (valor original + unidad). */
public record Weight(BigDecimal value, WeightUnit unit) {
}
