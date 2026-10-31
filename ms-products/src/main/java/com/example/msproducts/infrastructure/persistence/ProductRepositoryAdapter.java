package com.example.msproducts.infrastructure.persistence;

import com.example.msproducts.application.port.out.ProductRepositoryPort;
import com.example.msproducts.application.port.out.ResolvedMarketSku;
import com.example.msproducts.domain.model.Dimensions;
import com.example.msproducts.domain.model.DimensionUnit;
import com.example.msproducts.domain.model.MarketSku;
import com.example.msproducts.domain.model.PriceHistoryEntry;
import com.example.msproducts.domain.model.Product;
import com.example.msproducts.domain.model.ProductStatus;
import com.example.msproducts.domain.model.ProductVariant;
import com.example.msproducts.domain.model.VariantStatus;
import com.example.msproducts.domain.model.Weight;
import com.example.msproducts.domain.model.WeightUnit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
class ProductRepositoryAdapter implements ProductRepositoryPort {

    private final SpringDataProductRepository products;
    private final SpringDataVariantRepository variants;
    private final SpringDataMarketSkuRepository marketSkus;
    private final SpringDataPriceHistoryRepository history;
    private final EntityManager em;

    // ---- productos ----

    @Override
    public Product saveProduct(Product p) {
        return toDomain(products.saveAndFlush(ProductJpaEntity.builder()
                .id(p.getId()).name(p.getName()).brand(p.getBrand()).description(p.getDescription())
                .categoryId(p.getCategoryId()).hsCode(p.getHsCode()).imageUrl(p.getImageUrl())
                .status(p.getStatus().name()).createdBy(p.getCreatedBy()).updatedBy(p.getUpdatedBy())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).build()));
    }

    @Override
    public Optional<Product> findProductById(Long id) {
        return products.findById(id).map(this::toDomain);
    }

    @Override
    public List<Product> findProductsByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return products.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Product> searchProducts(String search, Set<Long> categoryIds, String brand, ProductStatus status) {
        StringBuilder jpql = new StringBuilder("select p from ProductJpaEntity p where 1 = 1");
        if (categoryIds != null) {
            jpql.append(" and p.categoryId in :categoryIds");
        }
        if (brand != null) {
            jpql.append(" and lower(p.brand) = lower(:brand)");
        }
        if (status != null) {
            jpql.append(" and p.status = :status");
        }
        if (search != null) {
            jpql.append(" and (lower(p.name) like :s escape '!' or lower(p.brand) like :s escape '!'")
                    .append(" or exists (select 1 from VariantJpaEntity v where v.productId = p.id")
                    .append(" and lower(v.sku) like :s escape '!'))");
        }
        jpql.append(" order by p.name, p.id");
        TypedQuery<ProductJpaEntity> query = em.createQuery(jpql.toString(), ProductJpaEntity.class);
        if (categoryIds != null) {
            query.setParameter("categoryIds", categoryIds);
        }
        if (brand != null) {
            query.setParameter("brand", brand);
        }
        if (status != null) {
            query.setParameter("status", status.name());
        }
        if (search != null) {
            query.setParameter("s", "%" + escapeLike(search.toLowerCase()) + "%");
        }
        return query.getResultList().stream().map(this::toDomain).toList();
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    // ---- variaciones ----

    @Override
    public ProductVariant saveVariant(ProductVariant v) {
        VariantJpaEntity saved = variants.saveAndFlush(toEntity(v));
        // Las columnas generadas (kg / cm) las calcula la base: se releen tras el guardado
        em.refresh(saved);
        return toDomain(saved);
    }

    @Override
    public Optional<ProductVariant> findVariantById(Long id) {
        return variants.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ProductVariant> findVariantBySku(String sku) {
        return variants.findBySku(sku).map(this::toDomain);
    }

    @Override
    public List<ResolvedMarketSku> findResolvedMarketSkus(Collection<Long> marketIds, Collection<String> marketSkuList) {
        if (marketIds.isEmpty() || marketSkuList.isEmpty()) {
            return List.of();
        }
        return marketSkus.findResolved(marketIds, marketSkuList);
    }

    @Override
    public List<String> findExistingVariantSkus(Collection<String> skus) {
        if (skus.isEmpty()) {
            return List.of();
        }
        return variants.findBySkuIn(skus).stream().map(VariantJpaEntity::getSku).toList();
    }

    @Override
    public List<ProductVariant> findVariantsBySkus(Collection<String> skus) {
        if (skus.isEmpty()) {
            return List.of();
        }
        return variants.findBySkuIn(skus).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ProductVariant> findVariantsByProductIds(Collection<Long> productIds) {
        return variants.findByProductIdInOrderByIdAsc(productIds).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ProductVariant> findMainVariant(Long productId) {
        return variants.findFirstByProductIdAndMainTrue(productId).map(this::toDomain);
    }

    // ---- equivalencias e historial ----

    @Override
    public MarketSku saveMarketSku(MarketSku m) {
        return toDomain(marketSkus.saveAndFlush(MarketSkuJpaEntity.builder()
                .id(m.id()).variantId(m.variantId()).marketId(m.marketId()).marketSku(m.marketSku())
                .createdBy(m.createdBy()).createdAt(m.createdAt()).build()));
    }

    @Override
    public Optional<MarketSku> findMarketSkuById(Long id) {
        return marketSkus.findById(id).map(this::toDomain);
    }

    @Override
    public List<MarketSku> findMarketSkusByVariantIds(Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return List.of();
        }
        return marketSkus.findByVariantIdInOrderByIdAsc(variantIds).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteMarketSku(Long id) {
        marketSkus.deleteById(id);
        marketSkus.flush();
    }

    @Override
    public List<PriceHistoryEntry> findPriceHistory(Long variantId) {
        return history.findByVariantIdOrderByRecordedAtAscIdAsc(variantId).stream()
                .map(h -> new PriceHistoryEntry(h.getId(), h.getVariantId(), h.getPrice(), h.getCurrency(),
                        h.getSourceUrl(), h.getRecordedAt()))
                .toList();
    }

    // ---- mapeos ----

    private Product toDomain(ProductJpaEntity e) {
        return Product.builder().id(e.getId()).name(e.getName()).brand(e.getBrand())
                .description(e.getDescription()).categoryId(e.getCategoryId()).hsCode(e.getHsCode())
                .imageUrl(e.getImageUrl()).status(ProductStatus.valueOf(e.getStatus()))
                .createdBy(e.getCreatedBy()).updatedBy(e.getUpdatedBy())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    private MarketSku toDomain(MarketSkuJpaEntity e) {
        return new MarketSku(e.getId(), e.getVariantId(), e.getMarketId(), e.getMarketSku(),
                e.getCreatedBy(), e.getCreatedAt());
    }

    private ProductVariant toDomain(VariantJpaEntity e) {
        Weight weight = e.getWeightValue() == null ? null
                : new Weight(e.getWeightValue(), WeightUnit.valueOf(e.getWeightUnit()));
        Dimensions dims = e.getLengthValue() == null ? null
                : new Dimensions(e.getLengthValue(), e.getWidthValue(), e.getHeightValue(),
                        DimensionUnit.valueOf(e.getDimensionUnit()));
        return ProductVariant.builder().id(e.getId()).productId(e.getProductId()).sku(e.getSku())
                .main(e.isMain()).color(e.getColor()).size(e.getSize()).barcode(e.getBarcode()).asin(e.getAsin())
                .weight(weight).weightKg(e.getWeightKg()).dimensions(dims)
                .lengthCm(e.getLengthCm()).widthCm(e.getWidthCm()).heightCm(e.getHeightCm())
                .referencePrice(e.getReferencePrice()).priceCurrency(e.getPriceCurrency())
                .priceSourceUrl(e.getPriceSourceUrl()).priceUpdatedAt(e.getPriceUpdatedAt())
                .imageUrl(e.getImageUrl()).status(VariantStatus.valueOf(e.getStatus()))
                .createdBy(e.getCreatedBy()).updatedBy(e.getUpdatedBy())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    private VariantJpaEntity toEntity(ProductVariant v) {
        Weight w = v.getWeight();
        Dimensions d = v.getDimensions();
        return VariantJpaEntity.builder().id(v.getId()).productId(v.getProductId()).sku(v.getSku())
                .main(v.isMain()).color(v.getColor()).size(v.getSize()).barcode(v.getBarcode()).asin(v.getAsin())
                .weightValue(w == null ? null : w.value()).weightUnit(w == null ? null : w.unit().name())
                .lengthValue(d == null ? null : d.length()).widthValue(d == null ? null : d.width())
                .heightValue(d == null ? null : d.height()).dimensionUnit(d == null ? null : d.unit().name())
                .referencePrice(v.getReferencePrice()).priceCurrency(v.getPriceCurrency())
                .priceSourceUrl(v.getPriceSourceUrl()).priceUpdatedAt(v.getPriceUpdatedAt())
                .imageUrl(v.getImageUrl()).status(v.getStatus().name())
                .createdBy(v.getCreatedBy()).updatedBy(v.getUpdatedBy())
                .createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt()).build();
    }
}
