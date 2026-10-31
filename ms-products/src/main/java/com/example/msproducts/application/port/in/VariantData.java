package com.example.msproducts.application.port.in;

import com.example.msproducts.domain.model.Dimensions;
import com.example.msproducts.domain.model.VariantStatus;
import com.example.msproducts.domain.model.Weight;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos de una variacion para crear o editar. {@code main} y {@code status} null:
 * al crear = no principal / ACTIVE; al editar = se conserva. En la edicion el SKU
 * y las equivalencias se ignoran, y un precio null conserva el vigente (lo escribe
 * tambien el scraper).
 */
public record VariantData(String sku, Boolean main, String color, String size, String barcode, String asin,
                          Weight weight, Dimensions dimensions, BigDecimal referencePrice, String priceCurrency,
                          String priceSourceUrl, String imageUrl, VariantStatus status,
                          List<MarketSkuData> marketSkus) {

    public List<MarketSkuData> marketSkus() {
        return marketSkus == null ? List.of() : marketSkus;
    }
}
