package com.example.msproducts.application.port.out;

import com.example.msproducts.domain.model.MarketSku;
import com.example.msproducts.domain.model.PriceHistoryEntry;
import com.example.msproducts.domain.model.Product;
import com.example.msproducts.domain.model.ProductStatus;
import com.example.msproducts.domain.model.ProductVariant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Productos, variaciones y equivalencias. Los guardados fuerzan el INSERT/UPDATE
 * y lanzan DataIntegrityViolationException si chocan un unico; las variaciones
 * vuelven con las columnas convertidas (kg / cm) ya leidas de la base.
 */
public interface ProductRepositoryPort {

    Product saveProduct(Product product);

    Optional<Product> findProductById(Long id);

    /** Una sola consulta. */
    List<Product> findProductsByIds(Collection<Long> ids);

    /** categoryIds null = sin filtro de categoria; search ya viene sin comodines escapados (texto crudo). */
    List<Product> searchProducts(String search, Set<Long> categoryIds, String brand, ProductStatus status);

    ProductVariant saveVariant(ProductVariant variant);

    Optional<ProductVariant> findVariantById(Long id);

    /** La base compara el SKU sin distinguir mayusculas. */
    Optional<ProductVariant> findVariantBySku(String sku);

    /**
     * Una sola consulta: equivalencias cuyo market esta en marketIds y cuyo SKU esta en marketSkus.
     * Es un superconjunto del producto cartesiano; el llamador filtra por par exacto.
     */
    List<ResolvedMarketSku> findResolvedMarketSkus(Collection<Long> marketIds, Collection<String> marketSkus);

    /** Una sola consulta: SKUs internos guardados que coinciden (sin distinguir mayusculas) con alguno de skus. */
    List<String> findExistingVariantSkus(Collection<String> skus);

    /** Una sola consulta: variaciones cuyo SKU coincide (sin distinguir mayusculas) con alguno de skus. */
    List<ProductVariant> findVariantsBySkus(Collection<String> skus);

    List<ProductVariant> findVariantsByProductIds(Collection<Long> productIds);

    Optional<ProductVariant> findMainVariant(Long productId);

    MarketSku saveMarketSku(MarketSku marketSku);

    Optional<MarketSku> findMarketSkuById(Long id);

    List<MarketSku> findMarketSkusByVariantIds(Collection<Long> variantIds);

    void deleteMarketSku(Long id);

    List<PriceHistoryEntry> findPriceHistory(Long variantId);
}
