package com.example.msproducts.application.port.in;

import java.util.List;

public record CreateProductCommand(ProductHeader header, List<VariantData> variants) {
}
