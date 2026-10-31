package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.application.port.in.MarketSkuData;
import com.example.msproducts.application.port.in.VariantData;
import com.example.msproducts.domain.model.VariantStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Alta de variacion (dentro de POST /products o POST /products/{id}/variants). */
/** Los @Size replican los largos de las columnas en schema.sql (sin ellos el exceso termina en 500). */
public record VariantRequest(@NotBlank @Size(max = 100) String sku, Boolean isMain,
                             @Size(max = 60) String color, @Size(max = 60) String size,
                             @Size(max = 50) String barcode, @Size(max = 20) String asin,
                             @Valid WeightDto weight, @Valid DimensionsDto dimensions,
                             BigDecimal referencePrice, @Size(max = 3) String priceCurrency,
                             @Size(max = 1000) String priceSourceUrl, @Size(max = 500) String imageUrl,
                             VariantStatus status, List<@Valid MarketSkuRequest> marketSkus) {

    public VariantData toData() {
        return new VariantData(sku, isMain, color, size, barcode, asin,
                weight == null ? null : weight.toDomain(), dimensions == null ? null : dimensions.toDomain(),
                referencePrice, priceCurrency, priceSourceUrl, imageUrl, status,
                marketSkus == null ? List.of()
                        : marketSkus.stream().map(m -> new MarketSkuData(m.marketId(), m.marketSku())).toList());
    }
}
