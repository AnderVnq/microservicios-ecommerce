package com.example.msproducts.application.port.in;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fila de la carga masiva tal como llega (sin validar): las unidades vienen como
 * texto para que una unidad desconocida rechace solo esa fila.
 */
public record ImportRow(String sku, String mainSku, String name, String brand, String description,
                        String categoryPath, String hsCode, String color, String size, String barcode, String asin,
                        RawWeight weight, RawDimensions dimensions, BigDecimal referencePrice, String priceCurrency,
                        String priceSourceUrl, String imageUrl, List<MarketSkuData> marketSkus) {

    public List<MarketSkuData> marketSkus() {
        return marketSkus == null ? List.of() : marketSkus;
    }

    public record RawWeight(BigDecimal value, String unit) {
    }

    public record RawDimensions(BigDecimal length, BigDecimal width, BigDecimal height, String unit) {
    }
}
