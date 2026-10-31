package com.example.msproducts.application.port.in;

import com.example.msproducts.domain.model.Product;

import java.util.List;

public record ProductView(Product product, List<String> categoryPath, List<VariantView> variants) {
}
