package com.example.msproducts.application.service;

import com.example.msproducts.application.port.in.CategoryUseCase;
import com.example.msproducts.application.port.in.CreateProductCommand;
import com.example.msproducts.application.port.in.MarketSkuData;
import com.example.msproducts.application.port.in.ProductHeader;
import com.example.msproducts.application.port.in.ProductUseCase;
import com.example.msproducts.application.port.in.ProductView;
import com.example.msproducts.application.port.in.VariantData;
import com.example.msproducts.application.port.in.VariantView;
import com.example.msproducts.application.port.out.ActorProvider;
import com.example.msproducts.application.port.out.MarketsPort;
import com.example.msproducts.application.port.out.ProductRepositoryPort;
import com.example.msproducts.domain.exception.MainVariantRequiredException;
import com.example.msproducts.domain.exception.MarketSkuNotFoundException;
import com.example.msproducts.domain.exception.ProductNotFoundException;
import com.example.msproducts.domain.exception.VariantNotFoundException;
import com.example.msproducts.domain.model.MarketSku;
import com.example.msproducts.domain.model.PriceHistoryEntry;
import com.example.msproducts.domain.model.Product;
import com.example.msproducts.domain.model.ProductStatus;
import com.example.msproducts.domain.model.ProductVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Las reglas de variaciones (SKU unico, una principal, equivalencia unica, peso y medidas,
 * moneda) viven aqui y en VariantRules para que la carga masiva las reutilice.
 * La base manda en las unicidades: se inserta y el choque del unico se traduce (VariantWriter).
 */
@Service
@RequiredArgsConstructor
@Transactional
class ProductService implements ProductUseCase {

    private final ProductRepositoryPort repository;
    private final CategoryUseCase categories;
    private final MarketsPort markets;
    private final ActorProvider actorProvider;
    private final VariantWriter writer;

    static final int DESCRIPTION_MAX_BYTES = 65_535;

    @Override
    public ProductView create(CreateProductCommand command) {
        List<VariantData> variants = command.variants();
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("El producto requiere al menos una variacion");
        }
        if (variants.stream().filter(v -> Boolean.TRUE.equals(v.main())).count() != 1) {
            throw new IllegalArgumentException("Exactamente una variacion debe ser la principal");
        }
        ProductHeader h = command.header();
        String actor = actorProvider.currentActor();
        Instant now = Instant.now();
        validateCategory(h.categoryId());
        Product saved = repository.saveProduct(Product.builder()
                .name(cleanName(h.name())).brand(h.brand()).description(checkDescription(h.description()))
                .categoryId(h.categoryId()).hsCode(h.hsCode()).imageUrl(h.imageUrl())
                .status(h.status() == null ? ProductStatus.ACTIVE : h.status())
                .createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now)
                .build());
        for (VariantData data : variants) {
            insertVariant(saved.getId(), data, Boolean.TRUE.equals(data.main()), actor);
        }
        return view(List.of(saved)).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView get(Long id) {
        Product product = repository.findProductById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return view(List.of(product)).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> search(String search, Long categoryId, String brand, ProductStatus status) {
        Set<Long> categoryIds = categoryId == null ? null : categories.selfAndDescendants(categoryId);
        String text = search == null || search.isBlank() ? null : search.trim();
        String brandFilter = brand == null || brand.isBlank() ? null : brand.trim();
        return view(repository.searchProducts(text, categoryIds, brandFilter, status));
    }

    @Override
    public ProductView update(Long id, ProductHeader h) {
        Product product = repository.findProductById(id).orElseThrow(() -> new ProductNotFoundException(id));
        validateCategory(h.categoryId());
        product.setName(cleanName(h.name()));
        product.setBrand(h.brand());
        product.setDescription(checkDescription(h.description()));
        product.setCategoryId(h.categoryId());
        product.setHsCode(h.hsCode());
        product.setImageUrl(h.imageUrl());
        if (h.status() != null) {
            product.setStatus(h.status());
        }
        product.setUpdatedBy(actorProvider.currentActor());
        product.setUpdatedAt(Instant.now());
        return view(List.of(repository.saveProduct(product))).get(0);
    }

    @Override
    public ProductVariant addVariant(Long productId, VariantData data) {
        repository.findProductById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        return insertVariant(productId, data, Boolean.TRUE.equals(data.main()), actorProvider.currentActor());
    }

    @Override
    public ProductVariant updateVariant(Long variantId, VariantData data) {
        ProductVariant variant = repository.findVariantById(variantId)
                .orElseThrow(() -> new VariantNotFoundException(variantId));
        String actor = actorProvider.currentActor();
        Instant now = Instant.now();
        VariantWriter.validateData(data);
        if (data.main() != null && data.main() != variant.isMain()) {
            if (!data.main()) {
                throw new MainVariantRequiredException();
            }
            writer.unmarkPreviousMain(variant.getProductId(), variantId, actor, now);
            variant.setMain(true);
        }
        variant.setColor(data.color());
        variant.setSize(data.size());
        variant.setBarcode(data.barcode());
        variant.setAsin(data.asin());
        variant.setWeight(data.weight());
        variant.setDimensions(data.dimensions());
        variant.setImageUrl(data.imageUrl());
        if (data.status() != null) {
            variant.setStatus(data.status());
        }
        if (data.referencePrice() != null) {
            boolean changed = variant.getReferencePrice() == null
                    || variant.getReferencePrice().compareTo(data.referencePrice()) != 0
                    || !data.priceCurrency().equals(variant.getPriceCurrency());
            variant.setReferencePrice(data.referencePrice());
            variant.setPriceCurrency(data.priceCurrency());
            variant.setPriceSourceUrl(data.priceSourceUrl());
            if (changed) {
                variant.setPriceUpdatedAt(now);
            }
        }
        variant.setUpdatedBy(actor);
        variant.setUpdatedAt(now);
        return repository.saveVariant(variant);
    }

    @Override
    @Transactional(readOnly = true)
    public VariantView getVariant(Long variantId) {
        ProductVariant variant = repository.findVariantById(variantId)
                .orElseThrow(() -> new VariantNotFoundException(variantId));
        return new VariantView(variant, repository.findMarketSkusByVariantIds(List.of(variantId)));
    }

    @Override
    public MarketSku addMarketSku(Long variantId, Long marketId, String marketSku) {
        repository.findVariantById(variantId).orElseThrow(() -> new VariantNotFoundException(variantId));
        return insertMarketSku(variantId, marketId, marketSku, actorProvider.currentActor());
    }

    @Override
    public void removeMarketSku(Long variantId, Long marketSkuId) {
        MarketSku existing = repository.findMarketSkuById(marketSkuId)
                .filter(m -> m.variantId().equals(variantId))
                .orElseThrow(() -> new MarketSkuNotFoundException(variantId, marketSkuId));
        repository.deleteMarketSku(existing.id());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceHistoryEntry> priceHistory(Long variantId) {
        repository.findVariantById(variantId).orElseThrow(() -> new VariantNotFoundException(variantId));
        return repository.findPriceHistory(variantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductVariant> findVariantBySku(String sku) {
        return sku == null || sku.isBlank() ? Optional.empty() : repository.findVariantBySku(sku.trim());
    }

    // ---- reglas de variacion ----

    private ProductVariant insertVariant(Long productId, VariantData data, boolean main, String actor) {
        for (MarketSkuData m : data.marketSkus()) {
            checkMarket(m.marketId());
        }
        return writer.insertVariant(productId, data, main, actor);
    }

    private MarketSku insertMarketSku(Long variantId, Long marketId, String marketSku, String actor) {
        checkMarket(marketId);
        return writer.insertMarketSku(variantId, marketId, marketSku, actor);
    }

    private void checkMarket(Long marketId) {
        if (marketId == null) {
            throw new IllegalArgumentException("El market es obligatorio");
        }
        if (!markets.exists(marketId)) {
            throw new IllegalArgumentException("El market %d no existe".formatted(marketId));
        }
    }

    private void validateCategory(Long categoryId) {
        if (categoryId != null) {
            categories.pathOf(categoryId);
        }
    }

    /** description es TEXT (65535 bytes): en UTF-8 un texto corto en caracteres puede no caber. */
    private static String checkDescription(String description) {
        if (description != null
                && description.getBytes(StandardCharsets.UTF_8).length > DESCRIPTION_MAX_BYTES) {
            throw new IllegalArgumentException("La descripcion supera %d bytes".formatted(DESCRIPTION_MAX_BYTES));
        }
        return description;
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }
        return name.trim();
    }

    // ---- vistas ----

    private List<ProductView> view(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<ProductVariant> variants = repository.findVariantsByProductIds(
                products.stream().map(Product::getId).toList());
        Map<Long, List<MarketSku>> skusByVariant = repository
                .findMarketSkusByVariantIds(variants.stream().map(ProductVariant::getId).toList()).stream()
                .collect(Collectors.groupingBy(MarketSku::variantId));
        Map<Long, List<VariantView>> byProduct = new HashMap<>();
        for (ProductVariant v : variants) {
            byProduct.computeIfAbsent(v.getProductId(), k -> new ArrayList<>())
                    .add(new VariantView(v, skusByVariant.getOrDefault(v.getId(), List.of())));
        }
        Map<Long, List<String>> paths = new HashMap<>();
        return products.stream().map(p -> new ProductView(p,
                p.getCategoryId() == null ? List.of()
                        : paths.computeIfAbsent(p.getCategoryId(), categories::pathOf),
                byProduct.getOrDefault(p.getId(), List.of()))).toList();
    }
}
