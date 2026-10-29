package com.example.msinventory.infrastructure.persistence;

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
        assertThat(type("inventory_units", "status")).isEqualTo("CHARACTER VARYING");
        assertThat(type("inventory_units", "warehouse")).isEqualTo("CHARACTER VARYING");
        assertThat(type("inventory_units", "entry_type")).isEqualTo("CHARACTER VARYING");
        assertThat(type("inventory_movements", "movement_type")).isEqualTo("CHARACTER VARYING");
        assertThat(type("inventory_movements", "direction")).isEqualTo("CHARACTER VARYING");
        assertThat(type("inventory_movements", "from_warehouse")).isEqualTo("CHARACTER VARYING");
        assertThat(type("inventory_entries", "entry_type")).isEqualTo("CHARACTER VARYING");
    }
}
