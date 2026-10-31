package com.example.msproducts.application.service;

import com.example.msproducts.application.port.in.IgnoredDifference;
import com.example.msproducts.application.port.in.ImportRow;
import com.example.msproducts.application.port.in.MarketSkuData;
import com.example.msproducts.application.port.in.RowOutcome;
import com.example.msproducts.application.port.in.RowResult;
import com.example.msproducts.domain.model.Dimensions;
import com.example.msproducts.domain.model.Weight;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Una fila de la carga masiva mientras se procesa: datos ya limpios (SKU sin espacios,
 * unidades convertidas a enum) y su resultado. Uso interno de ProductImportService.
 */
final class ImportLine {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    final int index;
    final ImportRow row;
    /** SKU sin espacios al inicio/fin (null si vino vacio). */
    final String sku;
    final String mainSku;
    /** Claves para comparar SKUs como la base: sin mayusculas ni acentos. */
    final String skuKey;
    final String mainKey;

    Weight weight;
    Dimensions dimensions;
    List<MarketSkuData> marketSkus = List.of();
    /** Ruta de categoria ya separada; solo se llena en la fila principal. */
    List<String> categoryPath = List.of();

    private RowOutcome outcome;
    private final List<String> reasons = new ArrayList<>();
    private final List<IgnoredDifference> differences = new ArrayList<>();

    ImportLine(int index, ImportRow row) {
        this.index = index;
        this.row = row;
        this.sku = row == null ? null : clean(row.sku());
        this.mainSku = row == null ? null : clean(row.mainSku());
        this.skuKey = key(sku);
        this.mainKey = key(mainSku);
    }

    boolean isMainRow() {
        return skuKey != null && skuKey.equals(mainKey);
    }

    boolean rejected() {
        return outcome == RowOutcome.REJECTED;
    }

    void reject(String reason) {
        outcome = RowOutcome.REJECTED;
        reasons.add(reason);
    }

    void done(RowOutcome result) {
        outcome = result;
    }

    void addDifferences(List<IgnoredDifference> found) {
        differences.addAll(found);
    }

    /** El bloque se revirtio: lo que se habia escrito de esta fila ya no existe. */
    void failBlock(String reason) {
        if (!rejected()) {
            differences.clear();
            reject(reason);
        }
    }

    RowResult toResult() {
        RowOutcome result = outcome == null ? RowOutcome.REJECTED : outcome;
        List<String> why = outcome == null ? List.of("Fila no procesada") : List.copyOf(reasons);
        return new RowResult(index, sku != null ? sku : row == null ? null : row.sku(), result, why,
                List.copyOf(differences));
    }

    /** Texto opcional: null o en blanco -> null; si no, sin espacios al inicio/fin. */
    static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Aproxima la collation de la base (utf8mb4_0900_ai_ci): sin mayusculas ni acentos. */
    static String key(String value) {
        if (value == null) {
            return null;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }

    static String marketKey(Long marketId, String marketSku) {
        return marketId + "|" + key(marketSku);
    }
}
