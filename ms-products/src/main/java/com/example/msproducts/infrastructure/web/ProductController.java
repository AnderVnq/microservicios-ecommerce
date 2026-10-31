package com.example.msproducts.infrastructure.web;

import com.example.msproducts.application.port.in.ProductImportUseCase;
import com.example.msproducts.application.port.in.ProductUseCase;
import com.example.msproducts.domain.model.ProductStatus;
import com.example.msproducts.infrastructure.web.dto.ImportRequest;
import com.example.msproducts.infrastructure.web.dto.ImportResponse;
import com.example.msproducts.infrastructure.web.dto.ProductRequest;
import com.example.msproducts.infrastructure.web.dto.ProductResponse;
import com.example.msproducts.infrastructure.web.dto.ProductUpdateRequest;
import com.example.msproducts.infrastructure.web.dto.VariantRequest;
import com.example.msproducts.infrastructure.web.dto.VariantResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Adaptador driver de productos: traduce HTTP al puerto ProductUseCase. */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductUseCase products;
    private final ProductImportUseCase importer;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(products.create(request.toCommand()));
    }

    @GetMapping
    public List<ProductResponse> search(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) String brand,
                                        @RequestParam(required = false) ProductStatus status) {
        return products.search(search, categoryId, brand, status).stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.from(products.get(id));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return ProductResponse.from(products.update(id, request.toHeader()));
    }

    @PostMapping("/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public VariantResponse addVariant(@PathVariable Long id, @Valid @RequestBody VariantRequest request) {
        Long variantId = products.addVariant(id, request.toData()).getId();
        return VariantResponse.from(products.getVariant(variantId));
    }

    /** Carga masiva: siempre 200 con el resultado por fila (salvo > 5000 filas -> 422, markets caido -> 503). */
    @PostMapping("/import")
    public ImportResponse importRows(@Valid @RequestBody ImportRequest request) {
        return ImportResponse.from(importer.importRows(request.toRows()));
    }
}
