package com.example.msproducts.application.service;

import com.example.msproducts.application.port.in.MarketSkuData;
import com.example.msproducts.application.port.in.VariantData;
import com.example.msproducts.application.port.out.ProductRepositoryPort;
import com.example.msproducts.domain.exception.MarketSkuAlreadyUsedException;
import com.example.msproducts.domain.exception.SkuAlreadyExistsException;
import com.example.msproducts.domain.model.MarketSku;
import com.example.msproducts.domain.model.ProductVariant;
import com.example.msproducts.domain.model.VariantRules;
import com.example.msproducts.domain.model.VariantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Escritura de variaciones y equivalencias compartida por el alta/edicion (ProductService)
 * y la carga masiva (ProductImportService). No valida que el market exista: cada llamador
 * lo hace a su manera (uno por equivalencia, o una sola vez por carga). Sin @Transactional:
 * corre dentro de la transaccion del llamador.
 */
@Component
@RequiredArgsConstructor
class VariantWriter {

    private static final String SKU_KEY = "uk_product_variants_sku";
    private static final String MARKET_SKU_KEY = "uk_variant_market_skus_market_sku";

    private final ProductRepositoryPort repository;

    ProductVariant insertVariant(Long productId, VariantData data, boolean main, String actor) {
        validateData(data);
        String sku = VariantRules.cleanCode(data.sku(), "El SKU");
        Instant now = Instant.now();
        if (main) {
            unmarkPreviousMain(productId, null, actor, now);
        }
        boolean priced = data.referencePrice() != null;
        ProductVariant saved;
        try {
            saved = repository.saveVariant(ProductVariant.builder()
                    .productId(productId).sku(sku).main(main)
                    .color(data.color()).size(data.size()).barcode(data.barcode()).asin(data.asin())
                    .weight(data.weight()).dimensions(data.dimensions())
                    .referencePrice(data.referencePrice())
                    .priceCurrency(priced ? data.priceCurrency() : null)
                    .priceSourceUrl(priced ? data.priceSourceUrl() : null)
                    .priceUpdatedAt(priced ? now : null)
                    .imageUrl(data.imageUrl())
                    .status(data.status() == null ? VariantStatus.ACTIVE : data.status())
                    .createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            if (violates(e, SKU_KEY)) {
                throw new SkuAlreadyExistsException(sku);
            }
            throw e;
        }
        for (MarketSkuData m : data.marketSkus()) {
            insertMarketSku(saved.getId(), m.marketId(), m.marketSku(), actor);
        }
        return saved;
    }

    MarketSku insertMarketSku(Long variantId, Long marketId, String rawMarketSku, String actor) {
        String marketSku = VariantRules.cleanCode(rawMarketSku, "El SKU del market");
        if (marketId == null) {
            throw new IllegalArgumentException("El market es obligatorio");
        }
        try {
            return repository.saveMarketSku(new MarketSku(null, variantId, marketId, marketSku, actor, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, MARKET_SKU_KEY)) {
                throw new MarketSkuAlreadyUsedException(marketId, marketSku);
            }
            throw e;
        }
    }

    void unmarkPreviousMain(Long productId, Long exceptVariantId, String actor, Instant now) {
        repository.findMainVariant(productId)
                .filter(prev -> !prev.getId().equals(exceptVariantId))
                .ifPresent(prev -> {
                    prev.setMain(false);
                    prev.setUpdatedBy(actor);
                    prev.setUpdatedAt(now);
                    repository.saveVariant(prev);
                });
    }

    static void validateData(VariantData data) {
        VariantRules.validateWeight(data.weight());
        VariantRules.validateDimensions(data.dimensions());
        VariantRules.validatePrice(data.referencePrice(), data.priceCurrency());
    }

    private static boolean violates(DataIntegrityViolationException e, String key) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return message != null && message.contains(key);
    }
}
