package com.example.msproducts.infrastructure.web;

import com.example.msproducts.application.port.in.ProductUseCase;
import com.example.msproducts.application.port.in.MarketSkuRef;
import com.example.msproducts.application.port.in.SkuResolutionUseCase;
import com.example.msproducts.domain.exception.VariantNotFoundException;
import com.example.msproducts.infrastructure.web.dto.MarketSkuRequest;
import com.example.msproducts.infrastructure.web.dto.MarketSkuResponse;
import com.example.msproducts.infrastructure.web.dto.PriceHistoryResponse;
import com.example.msproducts.infrastructure.web.dto.ResolveSkusRequest;
import com.example.msproducts.infrastructure.web.dto.SkuExistsRequest;
import com.example.msproducts.infrastructure.web.dto.SkuExistsResponse;
import com.example.msproducts.infrastructure.web.dto.SkuResolutionResponse;
import com.example.msproducts.infrastructure.web.dto.VariantResponse;
import com.example.msproducts.infrastructure.web.dto.VariantUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Adaptador driver de variaciones y sus equivalencias de market. */
@RestController
@RequestMapping("/api/v1/variants")
@RequiredArgsConstructor
public class VariantController {

    private final ProductUseCase products;
    private final SkuResolutionUseCase resolution;

    @GetMapping
    public VariantResponse bySku(@RequestParam String sku) {
        Long id = products.findVariantBySku(sku).orElseThrow(() -> new VariantNotFoundException(sku)).getId();
        return VariantResponse.from(products.getVariant(id));
    }

    @GetMapping("/{id}")
    public VariantResponse get(@PathVariable Long id) {
        return VariantResponse.from(products.getVariant(id));
    }

    @PutMapping("/{id}")
    public VariantResponse update(@PathVariable Long id, @Valid @RequestBody VariantUpdateRequest request) {
        products.updateVariant(id, request.toData());
        return VariantResponse.from(products.getVariant(id));
    }

    @PostMapping("/{id}/market-skus")
    @ResponseStatus(HttpStatus.CREATED)
    public MarketSkuResponse addMarketSku(@PathVariable Long id, @Valid @RequestBody MarketSkuRequest request) {
        return MarketSkuResponse.from(products.addMarketSku(id, request.marketId(), request.marketSku()));
    }

    @DeleteMapping("/{id}/market-skus/{marketSkuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMarketSku(@PathVariable Long id, @PathVariable Long marketSkuId) {
        products.removeMarketSku(id, marketSkuId);
    }

    @GetMapping("/{id}/price-history")
    public List<PriceHistoryResponse> priceHistory(@PathVariable Long id) {
        return products.priceHistory(id).stream().map(PriceHistoryResponse::from).toList();
    }

    @PostMapping("/resolve")
    public List<SkuResolutionResponse> resolve(@Valid @RequestBody ResolveSkusRequest request) {
        return resolution.resolve(request.marketSkus().stream()
                        .map(i -> new MarketSkuRef(i.marketId(), i.marketSku())).toList())
                .stream().map(SkuResolutionResponse::from).toList();
    }

    @PostMapping("/exists")
    public SkuExistsResponse exists(@Valid @RequestBody SkuExistsRequest request) {
        return SkuExistsResponse.of(request.skus(), resolution.canonical(request.skus()));
    }
}
