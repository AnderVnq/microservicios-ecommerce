package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.application.port.in.CreateProductCommand;
import com.example.msproducts.application.port.in.ProductHeader;
import com.example.msproducts.domain.model.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Los @Size replican los largos de las columnas en schema.sql; description es TEXT: el limite en bytes lo revisa el servicio. */
public record ProductRequest(@NotBlank @Size(max = 200) String name, @Size(max = 120) String brand,
                             @Size(max = 65_535) String description, Long categoryId,
                             @Size(max = 20) String hsCode, @Size(max = 500) String imageUrl, ProductStatus status,
                             @NotEmpty List<@Valid VariantRequest> variants) {

    public CreateProductCommand toCommand() {
        return new CreateProductCommand(new ProductHeader(name, brand, description, categoryId, hsCode, imageUrl,
                status), variants.stream().map(VariantRequest::toData).toList());
    }
}
