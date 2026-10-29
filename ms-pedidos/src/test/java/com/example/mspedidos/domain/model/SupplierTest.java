package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.SupplierInactiveException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierTest {

    @Test
    void supplier_invalidCountry_rejected() {
        assertThatThrownBy(() -> Supplier.create("Acme", "usa", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Supplier.create("Acme", "U", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Supplier.create(" ", "US", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_trimsNameAndIsActive() {
        Supplier s = Supplier.create("  Acme  ", "US", "addr", "desc");
        assertThat(s.getName()).isEqualTo("Acme");
        assertThat(s.getStatus()).isEqualTo(SupplierStatus.ACTIVE);
        s.ensureActive();
    }

    @Test
    void supplier_inactive_ensureActiveThrows() {
        Supplier s = Supplier.create("Acme", "US", null, null);
        s.update("Acme", "US", null, null, SupplierStatus.INACTIVE);
        assertThatThrownBy(s::ensureActive).isInstanceOf(SupplierInactiveException.class);
    }
}
