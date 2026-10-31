package com.example.msproducts.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Reglas de una variacion que no dependen de la base: las usan el alta y la edicion
 * de variaciones (y la carga masiva) para no duplicarlas. Todas fallan con
 * IllegalArgumentException (HTTP 422).
 */
public final class VariantRules {

    public static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("1000");
    public static final BigDecimal MAX_DIMENSION_CM = new BigDecimal("500");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    /** Escala de weight_value y de las columnas *_value (DECIMAL(12,4)). */
    static final int STORED_SCALE = 4;
    /** Escala de la columna generada weight_kg (DECIMAL(10,3)). */
    static final int KG_SCALE = 3;
    /** Escala de las columnas generadas *_cm (DECIMAL(10,2)). */
    static final int CM_SCALE = 2;

    private VariantRules() {
    }

    /** SKU o SKU de market: sin espacios al inicio/fin; vacio es invalido. */
    public static String cleanCode(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s es obligatorio".formatted(what));
        }
        return value.trim();
    }

    public static void validateWeight(Weight weight) {
        if (weight == null) {
            return;
        }
        if (weight.value() == null || weight.unit() == null) {
            throw new IllegalArgumentException("El peso requiere valor y unidad");
        }
        if (weight.value().signum() <= 0) {
            throw new IllegalArgumentException("El peso debe ser mayor que 0");
        }
        checkScale(weight.value(), "El peso");
        // La base redondea el kg generado a 3 decimales: un peso que quede en 0 kg no sirve
        if (roundsToZero(weight.unit().toKg(weight.value()), KG_SCALE)) {
            throw new IllegalArgumentException("El peso es demasiado pequeno: equivale a 0 kg con 3 decimales");
        }
        if (weight.unit().toKg(weight.value()).compareTo(MAX_WEIGHT_KG) > 0) {
            throw new IllegalArgumentException("El peso no puede superar 1000 kg");
        }
    }

    public static void validateDimensions(Dimensions d) {
        if (d == null) {
            return;
        }
        if (d.length() == null || d.width() == null || d.height() == null || d.unit() == null) {
            throw new IllegalArgumentException("Las medidas requieren largo, ancho, alto y unidad");
        }
        for (BigDecimal v : new BigDecimal[]{d.length(), d.width(), d.height()}) {
            if (v.signum() <= 0) {
                throw new IllegalArgumentException("Las medidas deben ser mayores que 0");
            }
            checkScale(v, "Las medidas");
            // La base redondea los cm generados a 2 decimales: una medida que quede en 0 cm no sirve
            if (roundsToZero(d.unit().toCm(v), CM_SCALE)) {
                throw new IllegalArgumentException("Las medidas son demasiado pequenas: equivalen a 0 cm con 2 decimales");
            }
            if (d.unit().toCm(v).compareTo(MAX_DIMENSION_CM) > 0) {
                throw new IllegalArgumentException("Ninguna medida puede superar 500 cm");
            }
        }
    }

    /** Mas de 4 decimales se truncaria/redondearia en la columna DECIMAL(12,4). */
    private static void checkScale(BigDecimal value, String what) {
        if (value.stripTrailingZeros().scale() > STORED_SCALE) {
            throw new IllegalArgumentException("%s admite como maximo %d decimales".formatted(what, STORED_SCALE));
        }
    }

    private static boolean roundsToZero(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).signum() == 0;
    }

    /** Con precio la moneda es obligatoria y debe ser un codigo de 3 letras mayusculas. */
    public static void validatePrice(BigDecimal price, String currency) {
        if (price != null && price.signum() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo");
        }
        if (price != null && currency == null) {
            throw new IllegalArgumentException("La moneda es obligatoria cuando hay precio");
        }
        if (currency != null && !CURRENCY.matcher(currency).matches()) {
            throw new IllegalArgumentException("La moneda debe ser un codigo de 3 letras mayusculas (ej. USD)");
        }
    }
}
