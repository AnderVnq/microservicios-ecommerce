package com.example.msproducts.domain.model;

import java.math.BigDecimal;

/**
 * Unidades de peso aceptadas. El factor solo sirve para VALIDAR limites en Java:
 * el valor en kg que se guarda y se devuelve lo calcula la columna generada de la base.
 */
public enum WeightUnit {
    LB("0.45359237"),
    OZ("0.028349523125"),
    KG("1"),
    G("0.001");

    private final BigDecimal kgFactor;

    WeightUnit(String kgFactor) {
        this.kgFactor = new BigDecimal(kgFactor);
    }

    public BigDecimal toKg(BigDecimal value) {
        return value.multiply(kgFactor);
    }

    public static WeightUnit parse(String unit) {
        if (unit != null) {
            for (WeightUnit u : values()) {
                if (u.name().equalsIgnoreCase(unit.trim())) {
                    return u;
                }
            }
        }
        throw new IllegalArgumentException("Unidad de peso desconocida: '%s' (use LB, OZ, KG o G)".formatted(unit));
    }
}
