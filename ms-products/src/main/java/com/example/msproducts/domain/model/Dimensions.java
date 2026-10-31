package com.example.msproducts.domain.model;

import java.math.BigDecimal;

/** Largo, ancho y alto tal como los escribio el usuario, en una misma unidad. */
public record Dimensions(BigDecimal length, BigDecimal width, BigDecimal height, DimensionUnit unit) {
}
