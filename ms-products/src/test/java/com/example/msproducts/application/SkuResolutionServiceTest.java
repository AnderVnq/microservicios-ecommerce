package com.example.msproducts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.msproducts.MySqlTestSupport;
import com.example.msproducts.application.port.in.MarketSkuRef;
import com.example.msproducts.application.port.in.SkuResolution;
import com.example.msproducts.application.port.in.SkuResolutionUseCase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SkuResolutionServiceTest extends MySqlTestSupport {

    @Autowired SkuResolutionUseCase resolution;

    @BeforeEach
    void datos() {
        jdbc.update("INSERT INTO products (id, name, status) VALUES (1, 'P', 'ACTIVE')");
        jdbc.update("INSERT INTO product_variants (id, product_id, sku, is_main, weight_value, weight_unit, status)"
                + " VALUES (10, 1, 'ZAP-X-42', 1, 1, 'KG', 'ACTIVE')");
        jdbc.update("INSERT INTO product_variants (id, product_id, sku, is_main, weight_value, weight_unit, status)"
                + " VALUES (11, 1, 'OLD-1', 0, 1, 'KG', 'INACTIVE')");
        jdbc.update("INSERT INTO product_variants (id, product_id, sku, is_main, weight_value, weight_unit, status)"
                + " VALUES (12, 1, 'FAL-1', 0, 1, 'KG', 'ACTIVE')");
        jdbc.update("INSERT INTO variant_market_skus (variant_id, market_id, market_sku) VALUES (10, 1, 'FAL-1')");
        jdbc.update("INSERT INTO variant_market_skus (variant_id, market_id, market_sku) VALUES (11, 2, 'AMZ-9')");
    }

    private static MarketSkuRef ref(long market, String sku) {
        return new MarketSkuRef(market, sku);
    }

    @Test
    void resolve_equivalenceWins_overInternalFallback() {
        // FAL-1 existe como SKU interno (variante 12) pero la equivalencia del market 1 apunta a ZAP-X-42
        List<SkuResolution> r = resolution.resolve(List.of(ref(1, "FAL-1")));
        assertThat(r).extracting(SkuResolution::sku).containsExactly("ZAP-X-42");
        // en otro market sin equivalencia cae al SKU interno
        assertThat(resolution.resolve(List.of(ref(3, "FAL-1")))).extracting(SkuResolution::sku)
                .containsExactly("FAL-1");
    }

    @Test
    void resolve_fallbackToInternalSku_caseInsensitive() {
        List<SkuResolution> r = resolution.resolve(List.of(ref(9, "  zap-x-42 ")));
        assertThat(r).extracting(SkuResolution::sku).containsExactly("ZAP-X-42");
    }

    @Test
    void resolve_unknown_null() {
        List<SkuResolution> r = resolution.resolve(List.of(ref(1, "NOPE")));
        assertThat(r).hasSize(1);
        assertThat(r.get(0).sku()).isNull();
    }

    @Test
    void resolve_inactiveVariant_resolves() {
        assertThat(resolution.resolve(List.of(ref(2, "amz-9"), ref(1, "old-1"))))
                .extracting(SkuResolution::sku).containsExactly("OLD-1", "OLD-1");
    }

    @Test
    void resolve_echoesInputCasing() {
        List<SkuResolution> r = resolution.resolve(List.of(ref(1, "fal-1"), ref(1, "fal-1")));
        assertThat(r).extracting(SkuResolution::marketSku).containsExactly("fal-1", "fal-1");
        assertThat(r).extracting(SkuResolution::marketId).containsExactly(1L, 1L);
        assertThat(r).extracting(SkuResolution::sku).containsExactly("ZAP-X-42", "ZAP-X-42");
    }

    @Test
    void resolve_over500_422() {
        List<MarketSkuRef> refs = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            refs.add(ref(1, "S" + i));
        }
        assertThatThrownBy(() -> resolution.resolve(refs)).isInstanceOf(IllegalArgumentException.class);
        assertThat(resolution.resolve(refs.subList(0, 500))).hasSize(500);
    }

    @Test
    void canonical_mapsInputToStoredSpelling_anyStatus() {
        java.util.Map<String, String> r = resolution.canonical(List.of("zap-x-42", "Old-1", "nope", " fal-1 "));
        // clave: la entrada tal como llego; valor: la escritura guardada (mayusculas, sin espacios)
        assertThat(r).containsExactly(java.util.Map.entry("zap-x-42", "ZAP-X-42"),
                java.util.Map.entry("Old-1", "OLD-1"), java.util.Map.entry(" fal-1 ", "FAL-1"));
        assertThatThrownBy(() -> resolution.canonical(java.util.Collections.nCopies(501, "a")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
