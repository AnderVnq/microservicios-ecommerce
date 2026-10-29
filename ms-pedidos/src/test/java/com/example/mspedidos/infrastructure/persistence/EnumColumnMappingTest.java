package com.example.mspedidos.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Los enums se guardan como varchar (no enum nativo de MySQL) para que nuevos valores no rompan el esquema. */
@DataJpaTest
class EnumColumnMappingTest {

    @Autowired
    JdbcTemplate jdbc;

    private String type(String table, String column) {
        return jdbc.queryForObject("""
                select data_type from information_schema.columns
                where lower(table_name) = ? and lower(column_name) = ?""", String.class, table, column);
    }

    @Test
    void enumColumnsAreVarchar() {
        assertThat(type("order_items", "importer_type")).isEqualTo("CHARACTER VARYING");
        assertThat(type("order_items", "status")).isEqualTo("CHARACTER VARYING");
        assertThat(type("order_items", "warehouse")).isEqualTo("CHARACTER VARYING");
        assertThat(type("order_items", "delivered_to")).isEqualTo("CHARACTER VARYING");
        assertThat(type("order_item_history", "event_type")).isEqualTo("CHARACTER VARYING");
        assertThat(type("item_incidents", "type")).isEqualTo("CHARACTER VARYING");
        assertThat(type("item_incidents", "scope")).isEqualTo("CHARACTER VARYING");
        assertThat(type("item_incidents", "status")).isEqualTo("CHARACTER VARYING");
        assertThat(type("status_change_requests", "status")).isEqualTo("CHARACTER VARYING");
    }
}
