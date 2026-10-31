package com.example.msproducts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class SchemaTest extends MySqlTestSupport {

    private Long productoId() {
        jdbc.update("INSERT INTO products (name, status) VALUES ('Producto', 'ACTIVE')");
        return jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
    }

    private Long variante(Long productId, String sku, String extraCols, Object... args) {
        Object[] all = new Object[args.length + 2];
        all[0] = productId;
        all[1] = sku;
        System.arraycopy(args, 0, all, 2, args.length);
        String marks = "?,".repeat(args.length);
        jdbc.update("INSERT INTO product_variants (product_id, sku, status" + extraCols + ") VALUES (?, ?, 'ACTIVE'"
                + (args.length == 0 ? "" : "," + marks.substring(0, marks.length() - 1)) + ")", all);
        return jdbc.queryForObject("SELECT id FROM product_variants WHERE sku = ?", Long.class, sku);
    }

    private List<Map<String, Object>> historial(Long variantId) {
        return jdbc.queryForList("SELECT * FROM variant_price_history WHERE variant_id = ? ORDER BY id", variantId);
    }

    @Test
    void priceTriggers_recordUtc_evenWhenScraperSessionIsInOtherZone() {
        Long p = productoId();
        Long id = variante(p, "TZ-1", ", reference_price, price_currency", new BigDecimal("1.00"), "USD");
        // SET y UPDATE en la MISMA conexion: el scraper escribe con la zona de su sesion
        List<Long> secondsOff = jdbc.execute((java.sql.Connection con) -> {
            try (java.sql.Statement st = con.createStatement()) {
                st.execute("SET time_zone = '-05:00'");
                try {
                    st.executeUpdate("UPDATE product_variants SET reference_price = 2.00 WHERE id = " + id);
                    st.executeUpdate("INSERT INTO product_variants (product_id, sku, status, reference_price,"
                            + " price_currency) VALUES (" + p + ", 'TZ-2', 'ACTIVE', 3.00, 'USD')");
                    java.sql.ResultSet rs = st.executeQuery(
                            "SELECT TIMESTAMPDIFF(SECOND, recorded_at, UTC_TIMESTAMP(6)) FROM variant_price_history"
                                    + " WHERE variant_id IN (SELECT id FROM product_variants WHERE product_id = " + p
                                    + ") ORDER BY id");
                    List<Long> out = new java.util.ArrayList<>();
                    while (rs.next()) {
                        out.add(rs.getLong(1));
                    }
                    return out;
                } finally {
                    st.execute("SET time_zone = 'UTC'");
                }
            }
        });
        // alta con precio inicial + cambio + alta TZ-2
        assertThat(secondsOff).hasSize(3).allSatisfy(d -> assertThat(Math.abs(d)).isLessThan(10));
    }

    @Test
    void generatedColumns_convertLbOzGKgInCm() {
        Long p = productoId();
        Long lb = variante(p, "LB-1", ", weight_value, weight_unit", new BigDecimal("2.5"), "LB");
        Long oz = variante(p, "OZ-1", ", weight_value, weight_unit", new BigDecimal("16"), "OZ");
        Long g = variante(p, "G-1", ", weight_value, weight_unit", new BigDecimal("500"), "G");
        Long kg = variante(p, "KG-1", ", weight_value, weight_unit", new BigDecimal("2"), "KG");
        Long in = variante(p, "IN-1", ", length_value, width_value, height_value, dimension_unit",
                new BigDecimal("10"), new BigDecimal("4"), new BigDecimal("3"), "IN");
        Long cm = variante(p, "CM-1", ", length_value, width_value, height_value, dimension_unit",
                new BigDecimal("10"), new BigDecimal("4"), new BigDecimal("3"), "CM");

        assertThat(jdbc.queryForObject("SELECT weight_kg FROM product_variants WHERE id = ?", BigDecimal.class, lb))
                .isEqualByComparingTo("1.134");
        assertThat(jdbc.queryForObject("SELECT weight_kg FROM product_variants WHERE id = ?", BigDecimal.class, oz))
                .isEqualByComparingTo("0.454");
        assertThat(jdbc.queryForObject("SELECT weight_kg FROM product_variants WHERE id = ?", BigDecimal.class, g))
                .isEqualByComparingTo("0.500");
        assertThat(jdbc.queryForObject("SELECT weight_kg FROM product_variants WHERE id = ?", BigDecimal.class, kg))
                .isEqualByComparingTo("2.000");
        Map<String, Object> dimIn = jdbc.queryForMap(
                "SELECT length_cm, width_cm, height_cm FROM product_variants WHERE id = ?", in);
        assertThat((BigDecimal) dimIn.get("length_cm")).isEqualByComparingTo("25.40");
        assertThat((BigDecimal) dimIn.get("width_cm")).isEqualByComparingTo("10.16");
        assertThat((BigDecimal) dimIn.get("height_cm")).isEqualByComparingTo("7.62");
        Map<String, Object> dimCm = jdbc.queryForMap(
                "SELECT length_cm, width_cm, height_cm FROM product_variants WHERE id = ?", cm);
        assertThat((BigDecimal) dimCm.get("length_cm")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) dimCm.get("width_cm")).isEqualByComparingTo("4.00");
        assertThat((BigDecimal) dimCm.get("height_cm")).isEqualByComparingTo("3.00");
    }

    @Test
    void generatedColumns_nullWhenNoValue() {
        Long p = productoId();
        Long sinNada = variante(p, "N-1", "");
        Long sinUnidad = variante(p, "N-2", ", weight_value, length_value", new BigDecimal("3"), new BigDecimal("3"));
        Long sinValor = variante(p, "N-3", ", weight_unit, dimension_unit", "LB", "IN");

        for (Long id : List.of(sinNada, sinUnidad, sinValor)) {
            Map<String, Object> fila = jdbc.queryForMap(
                    "SELECT weight_kg, length_cm, width_cm, height_cm FROM product_variants WHERE id = ?", id);
            assertThat(fila.values()).allMatch(v -> v == null);
        }
    }

    @Test
    void trigger_insertWithPrice_recordsHistory() {
        Long p = productoId();
        Long v = variante(p, "P-1", ", reference_price, price_currency, price_source_url",
                new BigDecimal("9.99"), "USD", "https://amazon.com/x");

        List<Map<String, Object>> h = historial(v);
        assertThat(h).hasSize(1);
        assertThat((BigDecimal) h.get(0).get("price")).isEqualByComparingTo("9.99");
        assertThat(h.get(0).get("currency")).isEqualTo("USD");
        assertThat(h.get(0).get("source_url")).isEqualTo("https://amazon.com/x");
        assertThat(h.get(0).get("recorded_at")).isNotNull();
    }

    @Test
    void trigger_insertWithoutPrice_recordsNothing() {
        Long v = variante(productoId(), "P-0", "");
        assertThat(historial(v)).isEmpty();
    }

    @Test
    void trigger_directSqlPriceUpdate_onlyRealChangesRecorded() {
        Long v = variante(productoId(), "P-2", "");
        jdbc.update("UPDATE product_variants SET reference_price = 10.00, price_currency = 'USD' WHERE id = ?", v);
        jdbc.update("UPDATE product_variants SET reference_price = 10.00, price_currency = 'USD' WHERE id = ?", v);
        jdbc.update("UPDATE product_variants SET reference_price = 12.00 WHERE id = ?", v);

        List<Map<String, Object>> h = historial(v);
        assertThat(h).hasSize(2);
        assertThat((BigDecimal) h.get(0).get("price")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) h.get(1).get("price")).isEqualByComparingTo("12.00");
    }

    @Test
    void trigger_currencyChange_recorded() {
        Long v = variante(productoId(), "P-3", ", reference_price, price_currency", new BigDecimal("5.00"), "USD");
        jdbc.update("UPDATE product_variants SET price_currency = 'EUR' WHERE id = ?", v);

        List<Map<String, Object>> h = historial(v);
        assertThat(h).hasSize(2);
        assertThat(h.get(1).get("currency")).isEqualTo("EUR");
    }

    @Test
    void topLevel_uniqueKey_blocksNullParentDuplicates() {
        jdbc.update("INSERT INTO categories (name, parent_id, level) VALUES ('Ropa', NULL, 1)");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO categories (name, parent_id, level) VALUES ('Ropa', NULL, 1)"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void sku_uniqueCaseInsensitive() {
        Long p = productoId();
        variante(p, "ABC-1", "");
        assertThatThrownBy(() -> variante(p, "abc-1", "")).isInstanceOf(DuplicateKeyException.class);
    }
}
