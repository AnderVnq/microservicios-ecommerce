package com.example.msproducts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.example.msproducts.MySqlTestSupport;
import com.example.msproducts.application.port.in.CreateProductCommand;
import com.example.msproducts.application.port.in.IgnoredDifference;
import com.example.msproducts.application.port.in.ImportResult;
import com.example.msproducts.application.port.in.ImportRow;
import com.example.msproducts.application.port.in.MarketSkuData;
import com.example.msproducts.application.port.in.ProductHeader;
import com.example.msproducts.application.port.in.ProductImportUseCase;
import com.example.msproducts.application.port.in.ProductUseCase;
import com.example.msproducts.application.port.in.ProductView;
import com.example.msproducts.application.port.in.RowOutcome;
import com.example.msproducts.application.port.in.RowResult;
import com.example.msproducts.application.port.in.VariantData;
import com.example.msproducts.domain.exception.MarketsUnavailableException;
import com.example.msproducts.domain.model.ProductVariant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductImportServiceTest extends MySqlTestSupport {

    @Autowired ProductImportUseCase importer;
    @Autowired ProductUseCase products;

    @BeforeEach
    void marketsExist() {
        when(marketsPort.exists(anyLong())).thenReturn(true);
    }

    // ---- helpers ----

    private static ImportRow row(String sku, String mainSku, String name, String categoryPath, String color,
                                 BigDecimal price, List<MarketSkuData> marketSkus) {
        return new ImportRow(sku, mainSku, name, "Acme", null, categoryPath, null, color, null, null, null,
                new ImportRow.RawWeight(new BigDecimal("2"), "LB"), null,
                price, price == null ? null : "USD", price == null ? null : "https://shop/" + sku, null, marketSkus);
    }

    private static ImportRow row(String sku, String mainSku) {
        return row(sku, mainSku, "Producto " + mainSku, null, null, null, null);
    }

    private static ImportRow withWeightUnit(String sku, String mainSku, String unit) {
        return new ImportRow(sku, mainSku, "Producto " + mainSku, null, null, null, null, null, null, null, null,
                new ImportRow.RawWeight(new BigDecimal("2"), unit), null, null, null, null, null, null);
    }

    private ProductView existing(String name, String mainSku, String... others) {
        List<VariantData> variants = new ArrayList<>();
        variants.add(new VariantData(mainSku, true, "Rojo", null, null, null, null, null,
                new BigDecimal("10.00"), "USD", "https://old", null, null, null));
        for (String sku : others) {
            variants.add(new VariantData(sku, false, null, null, null, null, null, null, null, null, null, null,
                    null, null));
        }
        return products.create(new CreateProductCommand(
                new ProductHeader(name, "Acme", null, null, null, null, null), variants));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static RowResult result(ImportResult r, int row) {
        return r.rows().get(row - 1);
    }

    private ProductVariant variant(String sku) {
        return products.findVariantBySku(sku).orElseThrow();
    }

    // ---- pruebas ----

    @Test
    void import_groupsByMainSku_createsProductVariantsCategories() {
        ImportResult r = importer.importRows(List.of(
                row("TS-1", "TS-1", "Camiseta", "Ropa > Hombre > Camisetas", "Rojo", new BigDecimal("19.99"),
                        List.of(new MarketSkuData(7L, "AMZ-TS1"))),
                row("TS-2", "TS-1", "Camiseta", null, "Azul", null, null),
                row("MG-1", "MG-1", "Taza", "Hogar", null, null, null)));

        assertThat(r.total()).isEqualTo(3);
        assertThat(r.created()).isEqualTo(3);
        assertThat(r.rows()).extracting(RowResult::result).containsOnly(RowOutcome.CREATED);
        assertThat(r.rows()).extracting(RowResult::row).containsExactly(1, 2, 3);
        assertThat(count("products")).isEqualTo(2);
        assertThat(count("categories")).isEqualTo(4);

        ProductVariant main = variant("TS-1");
        ProductVariant second = variant("TS-2");
        assertThat(main.isMain()).isTrue();
        assertThat(second.isMain()).isFalse();
        assertThat(second.getProductId()).isEqualTo(main.getProductId());
        assertThat(main.getWeightKg()).isEqualByComparingTo("0.907");
        assertThat(main.getReferencePrice()).isEqualByComparingTo("19.99");
        assertThat(main.getPriceUpdatedAt()).isNotNull();
        ProductView view = products.get(main.getProductId());
        assertThat(view.product().getName()).isEqualTo("Camiseta");
        assertThat(view.categoryPath()).containsExactly("Ropa", "Hombre", "Camisetas");
        assertThat(view.variants().get(0).marketSkus()).extracting(m -> m.marketSku()).containsExactly("AMZ-TS1");
    }

    @Test
    void import_existingSku_onlyPriceUpdated_differencesReported() {
        existing("Camiseta", "TS-1");

        ImportResult r = importer.importRows(List.of(
                row("TS-1", "TS-1", "Camiseta nueva", null, "Verde", new BigDecimal("12.50"),
                        List.of(new MarketSkuData(7L, "AMZ-TS1")))));

        RowResult row = result(r, 1);
        assertThat(row.result()).isEqualTo(RowOutcome.UPDATED);
        assertThat(row.ignoredDifferences()).contains(
                new IgnoredDifference("name", "Camiseta", "Camiseta nueva"),
                new IgnoredDifference("color", "Rojo", "Verde"));
        ProductVariant v = variant("TS-1");
        assertThat(v.getReferencePrice()).isEqualByComparingTo("12.50");
        assertThat(v.getPriceSourceUrl()).isEqualTo("https://shop/TS-1");
        assertThat(v.getColor()).isEqualTo("Rojo");
        assertThat(v.getWeight()).isNull();
        assertThat(products.get(v.getProductId()).product().getName()).isEqualTo("Camiseta");
        assertThat(products.getVariant(v.getId()).marketSkus()).extracting(m -> m.marketSku())
                .containsExactly("AMZ-TS1");
        assertThat(count("variant_price_history")).isEqualTo(2);
    }

    @Test
    void import_existingSku_noPriceFields_priceKept() {
        existing("Camiseta", "TS-1");

        ImportResult r = importer.importRows(List.of(row("TS-1", "TS-1", "Camiseta", null, "Rojo", null, null)));

        assertThat(result(r, 1).result()).isEqualTo(RowOutcome.UNCHANGED);
        assertThat(variant("TS-1").getReferencePrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void import_sameLoadTwice_allUnchanged() {
        List<ImportRow> load = List.of(
                row("TS-1", "TS-1", "Camiseta", "Ropa > Camisetas", "Rojo", new BigDecimal("19.99"),
                        List.of(new MarketSkuData(7L, "AMZ-TS1"))),
                row("TS-2", "TS-1", "Camiseta", null, "Azul", new BigDecimal("21"), null));
        importer.importRows(load);

        ImportResult second = importer.importRows(load);

        assertThat(second.unchanged()).isEqualTo(2);
        assertThat(second.rows()).allSatisfy(row -> {
            assertThat(row.result()).isEqualTo(RowOutcome.UNCHANGED);
            assertThat(row.ignoredDifferences()).isEmpty();
            assertThat(row.reasons()).isEmpty();
        });
        assertThat(count("products")).isEqualTo(1);
        assertThat(count("product_variants")).isEqualTo(2);
        assertThat(count("variant_market_skus")).isEqualTo(1);
        assertThat(count("variant_price_history")).isEqualTo(2);
    }

    @Test
    void import_skuDiffersOnlyInCase_matchesExisting() {
        existing("Camiseta", "ABC-1");

        ImportResult r = importer.importRows(List.of(row("abc-1", "abc-1", "Camiseta", null, "Rojo", null, null)));

        assertThat(result(r, 1).result()).isEqualTo(RowOutcome.UNCHANGED);
        assertThat(count("product_variants")).isEqualTo(1);
        assertThat(variant("ABC-1").getSku()).isEqualTo("ABC-1");
    }

    @Test
    void import_missingMainRow_groupRejected() {
        ImportResult r = importer.importRows(List.of(row("TS-2", "TS-1"), row("TS-3", "TS-1")));

        assertThat(r.rejected()).isEqualTo(2);
        assertThat(r.rows()).allSatisfy(row -> {
            assertThat(row.result()).isEqualTo(RowOutcome.REJECTED);
            assertThat(row.reasons()).anySatisfy(reason -> assertThat(reason).contains("sin fila principal"));
        });
        assertThat(count("products")).isZero();
    }

    @Test
    void import_existingProductWithoutMainRow_variantAdded() {
        Long productId = existing("Camiseta", "TS-1").product().getId();

        ImportResult r = importer.importRows(List.of(row("TS-9", "ts-1")));

        assertThat(result(r, 1).result()).isEqualTo(RowOutcome.CREATED);
        assertThat(variant("TS-9").getProductId()).isEqualTo(productId);
        assertThat(variant("TS-9").isMain()).isFalse();
        assertThat(variant("TS-1").isMain()).isTrue();
    }

    @Test
    void import_mainSkuIsNonMainVariant_rejected() {
        existing("Camiseta", "TS-1", "TS-2");

        ImportResult r = importer.importRows(List.of(row("TS-2", "TS-2"), row("TS-5", "TS-2")));

        assertThat(r.rejected()).isEqualTo(2);
        assertThat(r.rows()).extracting(RowResult::result).containsOnly(RowOutcome.REJECTED);
        assertThat(count("product_variants")).isEqualTo(2);
    }

    @Test
    void import_skuInOtherProduct_rejected() {
        existing("Taza", "MG-1");

        ImportResult r = importer.importRows(List.of(row("TS-1", "TS-1"), row("mg-1", "TS-1")));

        assertThat(result(r, 1).result()).isEqualTo(RowOutcome.CREATED);
        assertThat(result(r, 2).result()).isEqualTo(RowOutcome.REJECTED);
        assertThat(result(r, 2).reasons()).anySatisfy(reason -> assertThat(reason).contains("otro producto"));
        assertThat(count("product_variants")).isEqualTo(2);
    }

    @Test
    void import_badRow_doesNotAbortOthersInBlock() {
        ImportResult r = importer.importRows(List.of(
                row("TS-1", "TS-1"),
                withWeightUnit("TS-2", "TS-1", "STONE"),
                new ImportRow("TS-3", "TS-1", "Camiseta", null, null, null, null, null, null, null, null,
                        new ImportRow.RawWeight(new BigDecimal("2500"), "LB"), null, null, null, null, null, null),
                new ImportRow("TS-4", "TS-1", "Camiseta", null, null, null, null, "x".repeat(61), null, null, null,
                        null, null, null, null, null, null, null),
                new ImportRow("TS-5", "TS-1", "Camiseta", null, null, null, null, null, null, null, null, null,
                        null, new BigDecimal("12345678901.00"), "USD", null, null, null),
                row("TS-6", "TS-1"),
                row(" ", "TS-1")));

        assertThat(r.rows()).extracting(RowResult::result).containsExactly(RowOutcome.CREATED,
                RowOutcome.REJECTED, RowOutcome.REJECTED, RowOutcome.REJECTED, RowOutcome.REJECTED,
                RowOutcome.CREATED, RowOutcome.REJECTED);
        assertThat(result(r, 2).reasons()).anySatisfy(reason -> assertThat(reason).contains("STONE"));
        assertThat(result(r, 4).reasons()).anySatisfy(reason -> assertThat(reason).contains("color"));
        assertThat(count("product_variants")).isEqualTo(2);
    }

    @Test
    void import_invalidCategoryPath_groupRejected() {
        ImportResult r = importer.importRows(List.of(
                row("TS-1", "TS-1", "Camiseta", "A > B > C > D", null, null, null),
                row("TS-2", "TS-1"),
                row("MG-1", "MG-1", "Taza", "Hogar >  > Tazas", null, null, null)));

        assertThat(r.rows()).extracting(RowResult::result).containsOnly(RowOutcome.REJECTED);
        assertThat(count("products")).isZero();
        assertThat(count("categories")).isZero();
    }

    @Test
    void import_marketSkuTakenByOtherVariant_rowRejected() {
        Long variantId = existing("Taza", "MG-1").variants().get(0).variant().getId();
        products.addMarketSku(variantId, 7L, "AMZ-1");
        products.addMarketSku(variantId, 7L, "AMZ-2");

        ImportResult r = importer.importRows(List.of(
                row("TS-1", "TS-1"),
                row("TS-2", "TS-1", "Camiseta", null, null, null, List.of(new MarketSkuData(7L, "amz-1"))),
                row("MG-1", "MG-1", "Taza", null, null, null, List.of(new MarketSkuData(7L, "AMZ-2")))));

        assertThat(result(r, 1).result()).isEqualTo(RowOutcome.CREATED);
        assertThat(result(r, 2).result()).isEqualTo(RowOutcome.REJECTED);
        assertThat(result(r, 2).reasons()).anySatisfy(reason -> assertThat(reason).contains("amz-1"));
        // La equivalencia ya era de MG-1: no es un cambio
        assertThat(result(r, 3).result()).isEqualTo(RowOutcome.UNCHANGED);
        assertThat(count("variant_market_skus")).isEqualTo(2);
    }

    @Test
    void import_unknownMarket_rowsRejected() {
        when(marketsPort.exists(99L)).thenReturn(false);

        ImportResult r = importer.importRows(List.of(
                row("TS-1", "TS-1"),
                row("TS-2", "TS-1", "Camiseta", null, null, null, List.of(new MarketSkuData(99L, "X-1"))),
                row("TS-3", "TS-1", "Camiseta", null, null, null, List.of(new MarketSkuData(99L, "X-2")))));

        assertThat(r.rows()).extracting(RowResult::result).containsExactly(RowOutcome.CREATED,
                RowOutcome.REJECTED, RowOutcome.REJECTED);
        assertThat(result(r, 2).reasons()).anySatisfy(reason -> assertThat(reason).contains("99"));
        assertThat(count("variant_market_skus")).isZero();
        org.mockito.Mockito.verify(marketsPort, org.mockito.Mockito.times(1)).exists(99L);
    }

    @Test
    void import_marketsDown_503_nothingWritten() {
        when(marketsPort.exists(7L)).thenThrow(new MarketsUnavailableException("caido", null));

        assertThatThrownBy(() -> importer.importRows(List.of(
                row("MG-1", "MG-1", "Taza", "Hogar", null, null, null),
                row("TS-1", "TS-1", "Camiseta", null, null, null, List.of(new MarketSkuData(7L, "X-1"))))))
                .isInstanceOf(MarketsUnavailableException.class);

        assertThat(count("products")).isZero();
        assertThat(count("categories")).isZero();
    }

    @Test
    void import_over5000_422() {
        List<ImportRow> rows = Collections.nCopies(5001, row("TS-1", "TS-1"));

        assertThatThrownBy(() -> importer.importRows(rows)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("products")).isZero();
    }

    @Test
    void import_duplicateSkuInRequest_secondRejected() {
        ImportResult r = importer.importRows(List.of(row("TS-1", "TS-1"), row("TS-2", "TS-1"), row("ts-2 ", "TS-1")));

        assertThat(r.rows()).extracting(RowResult::result).containsExactly(RowOutcome.CREATED,
                RowOutcome.CREATED, RowOutcome.REJECTED);
        assertThat(result(r, 3).reasons()).containsExactly("SKU repetido en la carga");
        assertThat(count("product_variants")).isEqualTo(2);
    }

    @Test
    void import_unexpectedDbError_blockRolledBack_nextBlockContinues() {
        // Error imprevisto de la base: un trigger de prueba deja el SKU en NULL (columna NOT NULL)
        jdbc.execute("CREATE TRIGGER trg_test_boom BEFORE INSERT ON product_variants FOR EACH ROW "
                + "SET NEW.sku = IF(NEW.sku = 'BOOM', NULL, NEW.sku)");
        try {
            List<ImportRow> rows = new ArrayList<>();
            for (int p = 0; p < 125; p++) {
                for (int v = 0; v < 4; v++) {
                    rows.add(row(p == 60 && v == 2 ? "BOOM" : "P%d-V%d".formatted(p, v), "P%d-V0".formatted(p)));
                }
            }
            rows.add(row("LAST-1", "LAST-1"));

            ImportResult r = importer.importRows(rows);

            assertThat(r.rows().subList(0, 500)).allSatisfy(row -> {
                assertThat(row.result()).isEqualTo(RowOutcome.REJECTED);
                assertThat(row.reasons()).isNotEmpty();
            });
            assertThat(result(r, 501).result()).isEqualTo(RowOutcome.CREATED);
            assertThat(count("products")).isEqualTo(1);
            assertThat(count("product_variants")).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_test_boom");
        }
    }

    @Test
    void import_1200Rows_multipleBlocks_allCreated() {
        List<ImportRow> rows = new ArrayList<>();
        for (int p = 0; p < 300; p++) {
            for (int v = 0; v < 4; v++) {
                rows.add(row("P%d-V%d".formatted(p, v), "P%d-V0".formatted(p), "Producto " + p,
                        "Cat > Sub" + (p % 5), null, null, List.of(new MarketSkuData(7L, "M-%d-%d".formatted(p, v)))));
            }
        }

        ImportResult r = importer.importRows(rows);

        assertThat(r.created()).isEqualTo(1200);
        assertThat(r.rejected()).isZero();
        assertThat(count("products")).isEqualTo(300);
        assertThat(count("product_variants")).isEqualTo(1200);
        assertThat(count("variant_market_skus")).isEqualTo(1200);
        assertThat(count("categories")).isEqualTo(6);
        Map<String, Object> mains = jdbc.queryForMap(
                "SELECT COUNT(*) AS c FROM product_variants WHERE is_main = TRUE");
        assertThat(((Number) mains.get("c")).intValue()).isEqualTo(300);
    }
}
