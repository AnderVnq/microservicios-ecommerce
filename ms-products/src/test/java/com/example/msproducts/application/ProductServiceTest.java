package com.example.msproducts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.example.msproducts.MySqlTestSupport;
import com.example.msproducts.application.port.in.CategoryUseCase;
import com.example.msproducts.application.port.in.CreateProductCommand;
import com.example.msproducts.application.port.in.MarketSkuData;
import com.example.msproducts.application.port.in.ProductHeader;
import com.example.msproducts.application.port.in.ProductUseCase;
import com.example.msproducts.application.port.in.ProductView;
import com.example.msproducts.application.port.in.VariantData;
import com.example.msproducts.domain.exception.MainVariantRequiredException;
import com.example.msproducts.domain.exception.MarketSkuAlreadyUsedException;
import com.example.msproducts.domain.exception.MarketsUnavailableException;
import com.example.msproducts.domain.exception.SkuAlreadyExistsException;
import com.example.msproducts.domain.model.Category;
import com.example.msproducts.domain.model.DimensionUnit;
import com.example.msproducts.domain.model.Dimensions;
import com.example.msproducts.domain.model.MarketSku;
import com.example.msproducts.domain.model.PriceHistoryEntry;
import com.example.msproducts.domain.model.ProductVariant;
import com.example.msproducts.domain.model.Weight;
import com.example.msproducts.domain.model.WeightUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductServiceTest extends MySqlTestSupport {

    @Autowired ProductUseCase products;
    @Autowired CategoryUseCase categories;

    @BeforeEach
    void marketsExist() {
        when(marketsPort.exists(anyLong())).thenReturn(true);
    }

    private static ProductHeader header(String name, Long categoryId) {
        return new ProductHeader(name, "Acme", null, categoryId, null, null, null);
    }

    private static VariantData variant(String sku, boolean main) {
        return new VariantData(sku, main, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static VariantData variant(String sku, boolean main, Weight w, Dimensions d, BigDecimal price,
                                       List<MarketSkuData> skus) {
        return new VariantData(sku, main, "Rojo", "M", null, null, w, d, price, price == null ? null : "USD",
                null, null, null, skus);
    }

    private ProductView newProduct(String name, VariantData... variants) {
        return products.create(new CreateProductCommand(header(name, null), List.of(variants)));
    }

    @Test
    void create_productWithMainAndVariants_convertedValuesReturned() {
        ProductView view = products.create(new CreateProductCommand(header("Camiseta", null), List.of(
                variant("TS-1", true, new Weight(new BigDecimal("2"), WeightUnit.LB),
                        new Dimensions(new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("2"), DimensionUnit.IN),
                        new BigDecimal("19.99"), List.of(new MarketSkuData(7L, "AMZ-TS1"))),
                variant("TS-2", false))));

        assertThat(view.product().getId()).isNotNull();
        assertThat(view.product().getCreatedBy()).isEqualTo("op-1");
        assertThat(view.variants()).hasSize(2);
        ProductVariant main = view.variants().get(0).variant();
        assertThat(main.isMain()).isTrue();
        assertThat(main.getWeight().value()).isEqualByComparingTo("2");
        assertThat(main.getWeight().unit()).isEqualTo(WeightUnit.LB);
        assertThat(main.getWeightKg()).isEqualByComparingTo("0.907");
        assertThat(main.getLengthCm()).isEqualByComparingTo("25.40");
        assertThat(main.getWidthCm()).isEqualByComparingTo("12.70");
        assertThat(main.getHeightCm()).isEqualByComparingTo("5.08");
        assertThat(main.getReferencePrice()).isEqualByComparingTo("19.99");
        assertThat(main.getCreatedBy()).isEqualTo("op-1");
        assertThat(view.variants().get(0).marketSkus()).extracting(MarketSku::marketSku).containsExactly("AMZ-TS1");
        assertThat(view.variants().get(1).variant().isMain()).isFalse();
    }

    @Test
    void create_noMain_or_twoMain_422() {
        assertThatThrownBy(() -> newProduct("A", variant("A-1", false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newProduct("B", variant("B-1", true), variant("B-2", true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isZero();
    }

    @Test
    void create_duplicateSkuCaseInsensitive_409() {
        newProduct("A", variant("abc-1", true));

        assertThatThrownBy(() -> newProduct("B", variant("ABC-1", true)))
                .isInstanceOf(SkuAlreadyExistsException.class);
        // La transaccion completa se revierte: no queda el producto B huerfano
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(1);
    }

    @Test
    void skuTrimmed() {
        ProductView view = newProduct("A", variant("  sku-9  ", true));

        assertThat(view.variants().get(0).variant().getSku()).isEqualTo("sku-9");
        assertThat(products.findVariantBySku(" SKU-9 ")).isPresent();
    }

    @Test
    void updateVariant_switchMain_previousUnmarked() {
        ProductView view = newProduct("A", variant("A-1", true), variant("A-2", false));
        Long second = view.variants().get(1).variant().getId();

        products.updateVariant(second, variant(null, true));

        ProductView after = products.get(view.product().getId());
        assertThat(after.variants()).extracting(v -> v.variant().isMain()).containsExactly(false, true);
    }

    @Test
    void updateVariant_unmarkOnlyMain_409() {
        ProductView view = newProduct("A", variant("A-1", true), variant("A-2", false));
        Long first = view.variants().get(0).variant().getId();

        assertThatThrownBy(() -> products.updateVariant(first, variant(null, false)))
                .isInstanceOf(MainVariantRequiredException.class);
    }

    @Test
    void addMarketSku_duplicateInMarket_409() {
        Long a = newProduct("A", variant("A-1", true)).variants().get(0).variant().getId();
        Long b = newProduct("B", variant("B-1", true)).variants().get(0).variant().getId();
        products.addMarketSku(a, 7L, "X-1");

        assertThatThrownBy(() -> products.addMarketSku(b, 7L, " x-1 "))
                .isInstanceOf(MarketSkuAlreadyUsedException.class);
        // En otro market el mismo codigo si es libre
        assertThat(products.addMarketSku(b, 8L, "X-1").id()).isNotNull();
    }

    @Test
    void addMarketSku_sameVariantTwoSkusSameMarket_ok() {
        Long a = newProduct("A", variant("A-1", true)).variants().get(0).variant().getId();

        products.addMarketSku(a, 7L, "X-1");
        products.addMarketSku(a, 7L, "X-2");

        assertThat(products.getVariant(a).marketSkus()).hasSize(2);
    }

    @Test
    void removeMarketSku_removesIt() {
        Long a = newProduct("A", variant("A-1", true)).variants().get(0).variant().getId();
        MarketSku m = products.addMarketSku(a, 7L, "X-1");

        products.removeMarketSku(a, m.id());

        assertThat(products.getVariant(a).marketSkus()).isEmpty();
    }

    @Test
    void addMarketSku_unknownMarket_422() {
        Long a = newProduct("A", variant("A-1", true)).variants().get(0).variant().getId();
        when(marketsPort.exists(99L)).thenReturn(false);

        assertThatThrownBy(() -> products.addMarketSku(a, 99L, "X-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marketsDown_503() {
        Long a = newProduct("A", variant("A-1", true)).variants().get(0).variant().getId();
        when(marketsPort.exists(7L)).thenThrow(new MarketsUnavailableException("caido", null));

        assertThatThrownBy(() -> products.addMarketSku(a, 7L, "X-1")).isInstanceOf(MarketsUnavailableException.class);
    }

    @Test
    void weight_overLimit_422() {
        assertThatThrownBy(() -> newProduct("A", variant("A-1", true,
                new Weight(new BigDecimal("2205"), WeightUnit.LB), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newProduct("A", variant("A-1", true,
                new Weight(BigDecimal.ZERO, WeightUnit.KG), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        // 2204 LB = 999.76 kg: dentro del limite
        assertThat(newProduct("A", variant("A-1", true,
                new Weight(new BigDecimal("2204"), WeightUnit.LB), null, null, null)).variants()).hasSize(1);
    }

    @Test
    void dimensions_overLimit_and_badPrice_422() {
        assertThatThrownBy(() -> newProduct("A", variant("A-1", true, null,
                new Dimensions(new BigDecimal("197"), BigDecimal.ONE, BigDecimal.ONE, DimensionUnit.IN), null, null)))
                .isInstanceOf(IllegalArgumentException.class); // 197 in = 500.38 cm
        assertThatThrownBy(() -> newProduct("B", new VariantData("B-1", true, null, null, null, null, null, null,
                BigDecimal.TEN, "usd", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void search_byCategoryIncludesDescendants_andBySku() {
        Category root = categories.create("Ropa", null);
        Category child = categories.create("Camisetas", root.getId());
        Category other = categories.create("Calzado", null);
        products.create(new CreateProductCommand(header("Polo", child.getId()), List.of(variant("POLO-1", true))));
        products.create(new CreateProductCommand(header("Zapato", other.getId()), List.of(variant("ZAP-77", true))));

        assertThat(products.search(null, root.getId(), null, null)).extracting(v -> v.product().getName())
                .containsExactly("Polo");
        assertThat(products.search("zap-7", null, null, null)).extracting(v -> v.product().getName())
                .containsExactly("Zapato");
        assertThat(products.search("acme", null, null, null)).hasSize(2);
        assertThat(products.search("100%", null, null, null)).isEmpty();
        ProductView polo = products.search("polo", null, "ACME", null).get(0);
        assertThat(polo.categoryPath()).containsExactly("Ropa", "Camisetas");
    }

    @Test
    void priceHistory_afterApiPriceChange() {
        ProductView view = newProduct("A", variant("A-1", true, null, null, new BigDecimal("10.00"), null));
        Long id = view.variants().get(0).variant().getId();

        products.updateVariant(id, new VariantData(null, null, null, null, null, null, null, null,
                new BigDecimal("12.50"), "USD", "http://shop", null, null, null));
        // Mismo precio: no agrega fila
        products.updateVariant(id, new VariantData(null, null, null, null, null, null, null, null,
                new BigDecimal("12.50"), "USD", "http://shop", null, null, null));

        List<PriceHistoryEntry> history = products.priceHistory(id);
        assertThat(history).extracting(PriceHistoryEntry::price)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("12.50"));
        assertThat(history.get(1).currency()).isEqualTo("USD");
        assertThat(products.getVariant(id).variant().getPriceUpdatedAt()).isNotNull();
    }

    @Test
    void priceHistory_recordedAtSameClockAsJavaTimestamps() {
        Long id = newProduct("A", variant("A-1", true, null, null, new BigDecimal("10.00"), null))
                .variants().get(0).variant().getId();
        products.updateVariant(id, new VariantData(null, null, null, null, null, null, null, null,
                new BigDecimal("11.00"), "USD", null, null, null, null));

        PriceHistoryEntry last = products.priceHistory(id).get(1);
        java.time.Instant priceUpdatedAt = products.getVariant(id).variant().getPriceUpdatedAt();
        assertThat(java.time.Duration.between(last.recordedAt(), java.time.Instant.now()).abs().getSeconds())
                .isLessThan(10);
        assertThat(java.time.Duration.between(last.recordedAt(), priceUpdatedAt).abs().getSeconds()).isLessThan(5);
    }

    @Test
    void javaFactors_matchGeneratedColumns_forEveryUnit() {
        Long productId = newProduct("A", variant("BASE", true)).product().getId();
        BigDecimal[] values = {new BigDecimal("1"), new BigDecimal("2.5"), new BigDecimal("7.3333"),
                new BigDecimal("123.4567")};
        int n = 0;
        for (WeightUnit wu : WeightUnit.values()) {
            for (DimensionUnit du : DimensionUnit.values()) {
                for (BigDecimal v : values) {
                    String sku = "F-" + (n++);
                    jdbc.update("INSERT INTO product_variants (product_id, sku, is_main, weight_value, weight_unit,"
                            + " length_value, width_value, height_value, dimension_unit, status)"
                            + " VALUES (?, ?, FALSE, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                            productId, sku, v, wu.name(), v, v, v, du.name());
                    Map<String, Object> row = jdbc.queryForMap(
                            "SELECT weight_kg, length_cm, width_cm, height_cm FROM product_variants WHERE sku = ?", sku);
                    BigDecimal kg = wu.toKg(v).setScale(3, RoundingMode.HALF_UP);
                    BigDecimal cm = du.toCm(v).setScale(2, RoundingMode.HALF_UP);
                    assertThat((BigDecimal) row.get("weight_kg")).as(sku + " kg").isEqualByComparingTo(kg);
                    assertThat((BigDecimal) row.get("length_cm")).as(sku + " l").isEqualByComparingTo(cm);
                    assertThat((BigDecimal) row.get("width_cm")).as(sku + " w").isEqualByComparingTo(cm);
                    assertThat((BigDecimal) row.get("height_cm")).as(sku + " h").isEqualByComparingTo(cm);
                }
            }
        }
        assertThat(n).isEqualTo(32);
    }
}
