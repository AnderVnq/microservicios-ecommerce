package com.example.msproducts.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** Las columnas *_kg / *_cm son generadas por la base: solo lectura (se releen con refresh). */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariantJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    @Column(length = 60)
    private String color;

    @Column(length = 60)
    private String size;

    @Column(length = 50)
    private String barcode;

    @Column(length = 20)
    private String asin;

    @Column(name = "weight_value", precision = 12, scale = 4)
    private BigDecimal weightValue;

    @Column(name = "weight_unit", length = 40)
    private String weightUnit;

    @Column(name = "weight_kg", precision = 10, scale = 3, insertable = false, updatable = false)
    private BigDecimal weightKg;

    @Column(name = "length_value", precision = 12, scale = 4)
    private BigDecimal lengthValue;

    @Column(name = "width_value", precision = 12, scale = 4)
    private BigDecimal widthValue;

    @Column(name = "height_value", precision = 12, scale = 4)
    private BigDecimal heightValue;

    @Column(name = "dimension_unit", length = 40)
    private String dimensionUnit;

    @Column(name = "length_cm", precision = 10, scale = 2, insertable = false, updatable = false)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 10, scale = 2, insertable = false, updatable = false)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 10, scale = 2, insertable = false, updatable = false)
    private BigDecimal heightCm;

    @Column(name = "reference_price", precision = 12, scale = 2)
    private BigDecimal referencePrice;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "price_currency", length = 3)
    private String priceCurrency;

    @Column(name = "price_source_url", length = 1000)
    private String priceSourceUrl;

    @Column(name = "price_updated_at")
    private Instant priceUpdatedAt;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
