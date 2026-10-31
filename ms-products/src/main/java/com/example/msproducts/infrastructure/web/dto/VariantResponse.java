package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.application.port.in.VariantView;
import com.example.msproducts.domain.model.ProductVariant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** weight / dimensions traen el original y el valor convertido (kg / cm) calculado por la base. */
public record VariantResponse(Long id, Long productId, String sku, boolean isMain, String color, String size,
                              String barcode, String asin, WeightOut weight, DimensionsOut dimensions,
                              BigDecimal referencePrice, String priceCurrency, String priceSourceUrl,
                              Instant priceUpdatedAt, String imageUrl, String status,
                              List<MarketSkuResponse> marketSkus) {

    public record WeightOut(BigDecimal value, String unit, BigDecimal kg) {
    }

    public record DimensionsOut(BigDecimal length, BigDecimal width, BigDecimal height, String unit,
                                BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
    }

    public static VariantResponse from(VariantView view) {
        ProductVariant v = view.variant();
        WeightOut weight = v.getWeight() == null ? null
                : new WeightOut(v.getWeight().value(), v.getWeight().unit().name(), v.getWeightKg());
        DimensionsOut dims = v.getDimensions() == null ? null
                : new DimensionsOut(v.getDimensions().length(), v.getDimensions().width(),
                        v.getDimensions().height(), v.getDimensions().unit().name(),
                        v.getLengthCm(), v.getWidthCm(), v.getHeightCm());
        return new VariantResponse(v.getId(), v.getProductId(), v.getSku(), v.isMain(), v.getColor(), v.getSize(),
                v.getBarcode(), v.getAsin(), weight, dims, v.getReferencePrice(), v.getPriceCurrency(),
                v.getPriceSourceUrl(), v.getPriceUpdatedAt(), v.getImageUrl(), v.getStatus().name(),
                view.marketSkus().stream().map(MarketSkuResponse::from).toList());
    }
}
