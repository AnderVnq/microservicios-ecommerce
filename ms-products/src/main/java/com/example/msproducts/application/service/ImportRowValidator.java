package com.example.msproducts.application.service;

import com.example.msproducts.application.port.in.IgnoredDifference;
import com.example.msproducts.application.port.in.ImportRow;
import com.example.msproducts.application.port.in.MarketSkuData;
import com.example.msproducts.domain.exception.CategoryDepthExceededException;
import com.example.msproducts.domain.model.DimensionUnit;
import com.example.msproducts.domain.model.Dimensions;
import com.example.msproducts.domain.model.Product;
import com.example.msproducts.domain.model.ProductVariant;
import com.example.msproducts.domain.model.VariantRules;
import com.example.msproducts.domain.model.Weight;
import com.example.msproducts.domain.model.WeightUnit;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validaciones de una fila que no consultan la base (obligatorios, largos segun el
 * esquema, unidades, rangos, precio, ruta de categoria) y el calculo de las
 * "diferencias no aplicadas". Las reglas de peso, medidas y moneda son las de VariantRules.
 */
final class ImportRowValidator {

    /** Largos maximos de las columnas en schema.sql. */
    static final int SKU_MAX = 100;
    static final int NAME_MAX = 200;
    static final int BRAND_MAX = 120;
    static final int TEXT_MAX_BYTES = 65_535;
    static final int HS_CODE_MAX = 20;
    static final int COLOR_MAX = 60;
    static final int SIZE_MAX = 60;
    static final int BARCODE_MAX = 50;
    static final int ASIN_MAX = 20;
    static final int SOURCE_URL_MAX = 1000;
    static final int IMAGE_URL_MAX = 500;
    static final int CATEGORY_MAX = 120;

    private ImportRowValidator() {
    }

    /** Deja en la fila los datos convertidos o la rechaza con el motivo. */
    static void validate(ImportLine line, Set<Long> unknownMarkets) {
        ImportRow row = line.row;
        if (row == null) {
            line.reject("Fila vacia");
            return;
        }
        try {
            check(line.sku != null, "El SKU es obligatorio");
            check(line.mainSku != null, "El mainSku es obligatorio");
            check(ImportLine.clean(row.name()) != null, "El nombre es obligatorio");
            checkLengths(line);
            line.weight = weight(row.weight());
            line.dimensions = dimensions(row.dimensions());
            VariantRules.validateWeight(line.weight);
            VariantRules.validateDimensions(line.dimensions);
            VariantRules.validatePrice(row.referencePrice(), row.priceCurrency());
            checkPriceFits(row.referencePrice());
            line.marketSkus = marketSkus(row.marketSkus(), unknownMarkets);
            if (line.isMainRow()) {
                line.categoryPath = categoryPath(row.categoryPath());
            }
        } catch (IllegalArgumentException | CategoryDepthExceededException e) {
            line.reject(e.getMessage());
        }
    }

    private static void checkLengths(ImportLine line) {
        ImportRow r = line.row;
        max(line.sku, SKU_MAX, "sku");
        max(line.mainSku, SKU_MAX, "mainSku");
        max(r.name(), NAME_MAX, "name");
        max(r.brand(), BRAND_MAX, "brand");
        String description = ImportLine.clean(r.description());
        check(description == null || description.getBytes(StandardCharsets.UTF_8).length <= TEXT_MAX_BYTES,
                "El campo description supera %d bytes".formatted(TEXT_MAX_BYTES));
        max(r.hsCode(), HS_CODE_MAX, "hsCode");
        max(r.color(), COLOR_MAX, "color");
        max(r.size(), SIZE_MAX, "size");
        max(r.barcode(), BARCODE_MAX, "barcode");
        max(r.asin(), ASIN_MAX, "asin");
        max(r.priceSourceUrl(), SOURCE_URL_MAX, "priceSourceUrl");
        max(r.imageUrl(), IMAGE_URL_MAX, "imageUrl");
    }

    private static void max(String value, int max, String field) {
        String clean = ImportLine.clean(value);
        check(clean == null || clean.codePointCount(0, clean.length()) <= max,
                "El campo %s supera %d caracteres".formatted(field, max));
    }

    private static Weight weight(ImportRow.RawWeight w) {
        if (w == null) {
            return null;
        }
        check(w.value() != null, "El peso requiere valor y unidad");
        return new Weight(w.value(), WeightUnit.parse(w.unit()));
    }

    private static Dimensions dimensions(ImportRow.RawDimensions d) {
        if (d == null) {
            return null;
        }
        return new Dimensions(d.length(), d.width(), d.height(), DimensionUnit.parse(d.unit()));
    }

    /** Mas decimales de los que guarda la columna se redondearian en silencio (peso y medidas: VariantRules). */
    private static void checkScale(BigDecimal value, int scale, String what) {
        check(value == null || value.stripTrailingZeros().scale() <= scale,
                "%s admite como maximo %d decimales".formatted(what, scale));
    }

    /** reference_price es DECIMAL(12,2). */
    private static void checkPriceFits(BigDecimal price) {
        if (price == null) {
            return;
        }
        checkScale(price, 2, "El precio");
        check(price.precision() - price.scale() <= 10, "El precio supera el maximo admitido");
    }

    /** Equivalencias limpias y sin repetir dentro de la fila. */
    private static List<MarketSkuData> marketSkus(List<MarketSkuData> raw, Set<Long> unknownMarkets) {
        Map<String, MarketSkuData> distinct = new LinkedHashMap<>();
        for (MarketSkuData m : raw) {
            check(m != null && m.marketId() != null, "El market es obligatorio");
            String marketSku = VariantRules.cleanCode(m.marketSku(), "El SKU del market");
            max(marketSku, SKU_MAX, "marketSku");
            check(!unknownMarkets.contains(m.marketId()), "El market %d no existe".formatted(m.marketId()));
            distinct.putIfAbsent(ImportLine.marketKey(m.marketId(), marketSku),
                    new MarketSkuData(m.marketId(), marketSku));
        }
        return List.copyOf(distinct.values());
    }

    /** "Electronica > Audio > Audifonos" -> niveles sin espacios; vacia -> sin categoria. */
    static List<String> categoryPath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<String> names = Arrays.stream(path.split(">", -1)).map(String::trim).toList();
        check(names.stream().noneMatch(String::isEmpty), "La ruta de categoria tiene un nivel vacio");
        if (names.size() > CategoryDepthExceededException.MAX_LEVEL) {
            throw new CategoryDepthExceededException();
        }
        names.forEach(n -> max(n, CATEGORY_MAX, "categoryPath"));
        return names;
    }

    // ---- diferencias no aplicadas ----

    static List<IgnoredDifference> parentDifferences(Product p, List<String> currentPath, ImportLine line) {
        ImportRow r = line.row;
        List<IgnoredDifference> out = new ArrayList<>();
        text(out, "name", p.getName(), r.name());
        text(out, "brand", p.getBrand(), r.brand());
        text(out, "description", p.getDescription(), r.description());
        if (!line.categoryPath.isEmpty() && !line.categoryPath.equals(currentPath)) {
            out.add(new IgnoredDifference("categoryPath", currentPath.isEmpty() ? null : String.join(" > ", currentPath),
                    String.join(" > ", line.categoryPath)));
        }
        text(out, "hsCode", p.getHsCode(), r.hsCode());
        return out;
    }

    static List<IgnoredDifference> variantDifferences(ProductVariant v, ImportLine line) {
        ImportRow r = line.row;
        List<IgnoredDifference> out = new ArrayList<>();
        text(out, "color", v.getColor(), r.color());
        text(out, "size", v.getSize(), r.size());
        text(out, "barcode", v.getBarcode(), r.barcode());
        text(out, "asin", v.getAsin(), r.asin());
        if (line.weight != null && !sameWeight(v.getWeight(), line.weight)) {
            out.add(new IgnoredDifference("weight", format(v.getWeight()), format(line.weight)));
        }
        if (line.dimensions != null && !sameDimensions(v.getDimensions(), line.dimensions)) {
            out.add(new IgnoredDifference("dimensions", format(v.getDimensions()), format(line.dimensions)));
        }
        text(out, "imageUrl", v.getImageUrl(), r.imageUrl());
        return out;
    }

    /** Solo se informa lo que vino en la fila; el texto se compara distinguiendo mayusculas. */
    private static void text(List<IgnoredDifference> out, String field, String current, String received) {
        String clean = ImportLine.clean(received);
        if (clean != null && !clean.equals(current)) {
            out.add(new IgnoredDifference(field, current, clean));
        }
    }

    private static boolean sameWeight(Weight a, Weight b) {
        return a != null && a.unit() == b.unit() && a.value().compareTo(b.value()) == 0;
    }

    private static boolean sameDimensions(Dimensions a, Dimensions b) {
        return a != null && a.unit() == b.unit() && a.length().compareTo(b.length()) == 0
                && a.width().compareTo(b.width()) == 0 && a.height().compareTo(b.height()) == 0;
    }

    private static String format(Weight w) {
        return w == null ? null : plain(w.value()) + " " + w.unit();
    }

    private static String format(Dimensions d) {
        return d == null ? null
                : "%s x %s x %s %s".formatted(plain(d.length()), plain(d.width()), plain(d.height()), d.unit());
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    static boolean samePrice(ProductVariant v, BigDecimal price, String currency) {
        return v.getReferencePrice() != null && v.getReferencePrice().compareTo(price) == 0
                && Objects.equals(v.getPriceCurrency(), currency);
    }
}
