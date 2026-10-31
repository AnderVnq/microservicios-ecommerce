package com.example.msproducts.application.port.in;

import com.example.msproducts.domain.model.MarketSku;
import com.example.msproducts.domain.model.ProductVariant;

import java.util.List;

public record VariantView(ProductVariant variant, List<MarketSku> marketSkus) {
}
