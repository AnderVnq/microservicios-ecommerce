package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.ImporterQuotaUseCase;
import com.example.mspedidos.application.port.in.PersonQuota;
import com.example.mspedidos.application.port.in.QuotaSummary;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ImporterQuotaServiceTest extends ServiceTestSupport {

    @Autowired
    ImporterQuotaUseCase quotaUseCase;

    @Test
    void quotaFor_listsHolderThenActiveFamily_withUsage() {
        stubHolder(1L, dni("111"));
        when(clientesPort.listFamilyMembers(1L)).thenReturn(List.of(
                new Person(7L, "Familiar Activo", dni("333"), true),
                new Person(8L, "Familiar Inactivo", dni("444"), false)));
        seedUsedUnits(dni("111"), ImporterType.HOLDER, ORDER_DATE, 2);
        // la misma persona (DNI 333) consumio una unidad como familiar en la orden de otro cliente
        seedUsedUnits(dni("333"), ImporterType.FAMILY_MEMBER, ORDER_DATE, 1);
        // otro anio no cuenta
        seedUsedUnits(dni("333"), ImporterType.FAMILY_MEMBER, ORDER_DATE.minusYears(1), 2);

        QuotaSummary summary = quotaUseCase.quotaFor(1L, YEAR);

        assertThat(summary.year()).isEqualTo(YEAR);
        assertThat(summary.maxUnitsPerYear()).isEqualTo(3);
        assertThat(summary.persons()).containsExactly(
                new PersonQuota("HOLDER", 1L, "Titular 1", "DNI", "111", 2, 1),
                new PersonQuota("FAMILY_MEMBER", 7L, "Familiar Activo", "DNI", "333", 1, 2));
    }

    @Test
    void quotaFor_overLimit_availableIsZero() {
        stubHolder(1L, dni("111"));
        when(clientesPort.listFamilyMembers(1L)).thenReturn(List.of());
        seedUsedUnits(dni("111"), ImporterType.HOLDER, ORDER_DATE, 4);

        QuotaSummary summary = quotaUseCase.quotaFor(1L, YEAR);

        assertThat(summary.persons()).singleElement().satisfies(p -> {
            assertThat(p.usedUnits()).isEqualTo(4);
            assertThat(p.availableUnits()).isZero();
        });
    }
}
