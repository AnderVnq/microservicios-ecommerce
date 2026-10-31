package com.example.msproducts.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Variacion (SKU). weight / dimensions son los originales; los campos *Kg y *Cm
 * son los valores convertidos que calcula la base (solo lectura).
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class ProductVariant {

    private Long id;
    private Long productId;
    private String sku;
    private boolean main;
    private String color;
    private String size;
    private String barcode;
    private String asin;
    private Weight weight;
    private BigDecimal weightKg;
    private Dimensions dimensions;
    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;
    private BigDecimal referencePrice;
    private String priceCurrency;
    private String priceSourceUrl;
    private Instant priceUpdatedAt;
    private String imageUrl;
    private VariantStatus status;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
