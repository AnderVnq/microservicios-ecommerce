package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.application.port.in.VariantData;
import com.example.msproducts.domain.model.VariantStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Edicion de variacion: todo menos el SKU y las equivalencias. */
public record VariantUpdateRequest(Boolean isMain, @Size(max = 60) String color, @Size(max = 60) String size,
                                   @Size(max = 50) String barcode, @Size(max = 20) String asin,
                                   @Valid WeightDto weight, @Valid DimensionsDto dimensions,
                                   BigDecimal referencePrice, @Size(max = 3) String priceCurrency,
                                   @Size(max = 1000) String priceSourceUrl, @Size(max = 500) String imageUrl,
                                   VariantStatus status) {

    public VariantData toData() {
        return new VariantData(null, isMain, color, size, barcode, asin,
                weight == null ? null : weight.toDomain(), dimensions == null ? null : dimensions.toDomain(),
                referencePrice, priceCurrency, priceSourceUrl, imageUrl, status, null);
    }
}
