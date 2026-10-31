package com.example.msproducts.application.port.in;

import com.example.msproducts.domain.model.MarketSku;
import com.example.msproducts.domain.model.PriceHistoryEntry;
import com.example.msproducts.domain.model.ProductStatus;
import com.example.msproducts.domain.model.ProductVariant;

import java.util.List;
import java.util.Optional;

public interface ProductUseCase {

    /** Exactamente una variacion principal; SKU y equivalencias unicos. Todo en una transaccion. */
    ProductView create(CreateProductCommand command);

    ProductView get(Long id);

    /**
     * search coincide (contiene, sin distinguir mayusculas) con nombre, marca o SKU de
     * cualquier variacion; categoryId incluye sus descendientes. Los filtros null no filtran.
     */
    List<ProductView> search(String search, Long categoryId, String brand, ProductStatus status);

    ProductView update(Long id, ProductHeader header);

    /** isMain=true desmarca la principal anterior en la misma transaccion. */
    ProductVariant addVariant(Long productId, VariantData data);

    /** No cambia el SKU. isMain=false sobre la principal actual falla con MAIN_VARIANT_REQUIRED. */
    ProductVariant updateVariant(Long variantId, VariantData data);

    VariantView getVariant(Long variantId);

    MarketSku addMarketSku(Long variantId, Long marketId, String marketSku);

    void removeMarketSku(Long variantId, Long marketSkuId);

    List<PriceHistoryEntry> priceHistory(Long variantId);

    Optional<ProductVariant> findVariantBySku(String sku);
}
