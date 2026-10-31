package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.application.port.in.ProductHeader;
import com.example.msproducts.domain.model.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(@NotBlank @Size(max = 200) String name, @Size(max = 120) String brand,
                                   @Size(max = 65_535) String description, Long categoryId,
                                   @Size(max = 20) String hsCode, @Size(max = 500) String imageUrl,
                                   ProductStatus status) {

    public ProductHeader toHeader() {
        return new ProductHeader(name, brand, description, categoryId, hsCode, imageUrl, status);
    }
}
