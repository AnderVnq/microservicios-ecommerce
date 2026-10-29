package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.IncidentAlreadyResolvedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemIncidentTest {

    @Test
    void importerPendingIncident_isExternalAndOpen() {
        ItemIncident incident = ItemIncident.importerPending(5L, "system");

        assertThat(incident.getOrderItemId()).isEqualTo(5L);
        assertThat(incident.getType()).isEqualTo(IncidentType.IMPORTER_ASSIGNMENT_PENDING);
        assertThat(incident.getScope()).isEqualTo(IncidentScope.EXTERNAL);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getDescription()).isEqualTo("Unidad excede el cupo del titular; asignar importador");
        assertThat(incident.getOpenedBy()).isEqualTo("system");
        assertThat(incident.getOpenedAt()).isNotNull();
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getResolvedBy()).isNull();
    }

    @Test
    void open_createsOpenIncidentWithGivenValues() {
        ItemIncident incident = ItemIncident.open(2L, IncidentType.DELAY, IncidentScope.INTERNAL, "late", "op");

        assertThat(incident.getType()).isEqualTo(IncidentType.DELAY);
        assertThat(incident.getScope()).isEqualTo(IncidentScope.INTERNAL);
        assertThat(incident.getDescription()).isEqualTo("late");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getOpenedBy()).isEqualTo("op");
        assertThat(incident.getOpenedAt()).isNotNull();
    }

    @Test
    void resolve_setsResolvedFields() {
        ItemIncident incident = ItemIncident.open(2L, IncidentType.OTHER, IncidentScope.INTERNAL, "x", "op");

        incident.resolve("boss");

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedBy()).isEqualTo("boss");
        assertThat(incident.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveTwice_throws() {
        ItemIncident incident = ItemIncident.open(2L, IncidentType.OTHER, IncidentScope.INTERNAL, "x", "op");
        incident.resolve("boss");

        assertThatThrownBy(() -> incident.resolve("other"))
                .isInstanceOf(IncidentAlreadyResolvedException.class);
        assertThat(incident.getResolvedBy()).isEqualTo("boss");
    }

    @Test
    void onlyImporterPendingIsSystemManaged() {
        assertThat(IncidentType.IMPORTER_ASSIGNMENT_PENDING.isSystemManaged()).isTrue();
        assertThat(IncidentType.DELAY.isSystemManaged()).isFalse();
        assertThat(IncidentType.VARIANT_CHANGE.isSystemManaged()).isFalse();
        assertThat(IncidentType.OTHER.isSystemManaged()).isFalse();
    }
}
