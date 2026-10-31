package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.application.port.in.ProductView;
import com.example.msproducts.domain.model.Product;

import java.util.List;

public record ProductResponse(Long id, String name, String brand, String description, Long categoryId,
                              List<String> categoryPath, String hsCode, String imageUrl, String status,
                              List<VariantResponse> variants) {

    public static ProductResponse from(ProductView view) {
        Product p = view.product();
        return new ProductResponse(p.getId(), p.getName(), p.getBrand(), p.getDescription(), p.getCategoryId(),
                view.categoryPath(), p.getHsCode(), p.getImageUrl(), p.getStatus().name(),
                view.variants().stream().map(VariantResponse::from).toList());
    }
}
