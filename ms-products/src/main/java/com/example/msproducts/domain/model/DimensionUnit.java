package com.example.msproducts.domain.model;

import java.math.BigDecimal;

/** Unidades de medida; igual que el peso, el factor solo valida limites (la base calcula los cm guardados). */
public enum DimensionUnit {
    IN("2.54"),
    CM("1");

    private final BigDecimal cmFactor;

    DimensionUnit(String cmFactor) {
        this.cmFactor = new BigDecimal(cmFactor);
    }

    public BigDecimal toCm(BigDecimal value) {
        return value.multiply(cmFactor);
    }

    public static DimensionUnit parse(String unit) {
        if (unit != null) {
            for (DimensionUnit u : values()) {
                if (u.name().equalsIgnoreCase(unit.trim())) {
                    return u;
                }
            }
        }
        throw new IllegalArgumentException("Unidad de medida desconocida: '%s' (use IN o CM)".formatted(unit));
    }
}
