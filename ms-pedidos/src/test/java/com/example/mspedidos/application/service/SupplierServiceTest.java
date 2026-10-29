package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.SupplierUseCase;
import com.example.mspedidos.domain.exception.SupplierNameTakenException;
import com.example.mspedidos.domain.exception.SupplierNotFoundException;
import com.example.mspedidos.domain.model.Supplier;
import com.example.mspedidos.domain.model.SupplierStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierServiceTest extends ServiceTestSupport {

    @Autowired
    SupplierUseCase useCase;

    @Test
    void create_activeByDefault() {
        Supplier s = useCase.create("Acme", "US", "Calle 1", "desc");

        assertThat(s.getId()).isNotNull();
        assertThat(s.getStatus()).isEqualTo(SupplierStatus.ACTIVE);
        assertThat(useCase.findOne(s.getId()).getName()).isEqualTo("Acme");
    }

    @Test
    void create_duplicateNameDifferentCase_409() {
        useCase.create("Acme", "US", null, null);

        assertThatThrownBy(() -> useCase.create("  ACME ", "CN", null, null))
                .isInstanceOf(SupplierNameTakenException.class);
    }

    @Test
    void patch_deactivate_keepsOtherFields() {
        Supplier s = useCase.create("Acme", "US", "Calle 1", "desc");

        Supplier updated = useCase.update(s.getId(), null, null, null, null, SupplierStatus.INACTIVE);

        assertThat(updated.getStatus()).isEqualTo(SupplierStatus.INACTIVE);
        assertThat(updated.getName()).isEqualTo("Acme");
        assertThat(updated.getCountry()).isEqualTo("US");
        assertThat(updated.getAddress()).isEqualTo("Calle 1");
        assertThat(updated.getDescription()).isEqualTo("desc");
    }

    @Test
    void patch_renameToOtherSupplierName_409_butSameNameAllowed() {
        useCase.create("Acme", "US", null, null);
        Supplier b = useCase.create("Beta", "US", null, null);

        assertThat(useCase.update(b.getId(), "beta", null, null, null, null).getName()).isEqualTo("beta");
        assertThatThrownBy(() -> useCase.update(b.getId(), "ACME", null, null, null, null))
                .isInstanceOf(SupplierNameTakenException.class);
    }

    @Test
    void findOne_unknown_404() {
        assertThatThrownBy(() -> useCase.findOne(999L)).isInstanceOf(SupplierNotFoundException.class);
        assertThatThrownBy(() -> useCase.update(999L, "x", null, null, null, null))
                .isInstanceOf(SupplierNotFoundException.class);
    }

    @Test
    void search_filters() {
        useCase.create("Acme", "US", null, null);
        Supplier b = useCase.create("Beta", "CN", null, null);
        useCase.update(b.getId(), null, null, null, null, SupplierStatus.INACTIVE);

        assertThat(useCase.search(null, null, null)).hasSize(2);
        List<Supplier> r = useCase.search(SupplierStatus.INACTIVE, "CN", "bet");
        assertThat(r).extracting(Supplier::getName).containsExactly("Beta");
    }
}
