package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.IncidentUseCase;
import com.example.mspedidos.domain.exception.IncidentNotFoundException;
import com.example.mspedidos.domain.exception.SystemManagedIncidentException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentServiceTest extends ServiceTestSupport {

    @Autowired
    IncidentUseCase useCase;

    private OrderItem unit() {
        return seedUnassignedUnits(1L, ORDER_DATE, 1).get(0);
    }

    @Test
    void open_delay_writesHistory() {
        OrderItem unit = unit();

        ItemIncident incident = useCase.open(unit.getOrderId(), unit.getId(), IncidentType.DELAY,
                IncidentScope.EXTERNAL, "Retraso del proveedor");

        assertThat(incident.getId()).isNotNull();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getOpenedBy()).isEqualTo("op-1");
        assertThat(incidentRepository.findOpen(unit.getId(), IncidentType.DELAY)).isPresent();
        OrderItemHistoryEntry h = historyRepository.findByOrderItemId(unit.getId()).stream()
                .filter(e -> e.eventType() == HistoryEventType.INCIDENT_OPENED).findFirst().orElseThrow();
        assertThat(h.fromValue()).isNull();
        assertThat(h.toValue()).isEqualTo("DELAY");
        assertThat(h.performedBy()).isEqualTo("op-1");
    }

    @Test
    void open_importerPending_rejected() {
        OrderItem unit = unit();

        assertThatThrownBy(() -> useCase.open(unit.getOrderId(), unit.getId(),
                IncidentType.IMPORTER_ASSIGNMENT_PENDING, IncidentScope.INTERNAL, "x"))
                .isInstanceOf(SystemManagedIncidentException.class);
        assertThat(incidentRepository.search(null, null, null)).isEmpty();
        assertThat(historyCount(unit.getId(), HistoryEventType.INCIDENT_OPENED)).isZero();
    }

    @Test
    void resolve_importerPending_rejected() {
        OrderItem unit = unit();
        ItemIncident pending = incidentRepository.save(ItemIncident.importerPending(unit.getId(), "system"));

        assertThatThrownBy(() -> useCase.resolve(pending.getId()))
                .isInstanceOf(SystemManagedIncidentException.class);
        assertThat(incidentRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.OPEN);
        assertThat(historyCount(unit.getId(), HistoryEventType.INCIDENT_RESOLVED)).isZero();
    }

    @Test
    void resolve_delay_resolvesAndWritesHistory() {
        OrderItem unit = unit();
        ItemIncident incident = useCase.open(unit.getOrderId(), unit.getId(), IncidentType.DELAY,
                IncidentScope.EXTERNAL, "d");

        ItemIncident resolved = useCase.resolve(incident.getId());

        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedBy()).isEqualTo("op-1");
        assertThat(incidentRepository.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.RESOLVED);
        OrderItemHistoryEntry h = historyRepository.findByOrderItemId(unit.getId()).stream()
                .filter(e -> e.eventType() == HistoryEventType.INCIDENT_RESOLVED).findFirst().orElseThrow();
        assertThat(h.fromValue()).isEqualTo("DELAY");
        assertThat(h.toValue()).isNull();
    }

    @Test
    void search_filtersByStatusTypeScope() {
        OrderItem unit = unit();
        ItemIncident delay = useCase.open(unit.getOrderId(), unit.getId(), IncidentType.DELAY,
                IncidentScope.EXTERNAL, "d");
        ItemIncident variant = useCase.open(unit.getOrderId(), unit.getId(), IncidentType.VARIANT_CHANGE,
                IncidentScope.INTERNAL, "v");
        ItemIncident other = useCase.open(unit.getOrderId(), unit.getId(), IncidentType.OTHER,
                IncidentScope.INTERNAL, "o");
        useCase.resolve(other.getId());

        assertThat(useCase.search(null, null, null)).extracting(ItemIncident::getId)
                .containsExactlyInAnyOrder(delay.getId(), variant.getId(), other.getId());
        assertThat(useCase.search(IncidentStatus.OPEN, null, null)).extracting(ItemIncident::getId)
                .containsExactlyInAnyOrder(delay.getId(), variant.getId());
        assertThat(useCase.search(IncidentStatus.RESOLVED, null, null)).extracting(ItemIncident::getId)
                .containsExactly(other.getId());
        assertThat(useCase.search(null, IncidentType.DELAY, null)).extracting(ItemIncident::getId)
                .containsExactly(delay.getId());
        assertThat(useCase.search(null, null, IncidentScope.INTERNAL)).extracting(ItemIncident::getId)
                .containsExactlyInAnyOrder(variant.getId(), other.getId());
        assertThat(useCase.search(IncidentStatus.OPEN, IncidentType.VARIANT_CHANGE, IncidentScope.INTERNAL))
                .extracting(ItemIncident::getId).containsExactly(variant.getId());
        assertThat(useCase.search(IncidentStatus.RESOLVED, IncidentType.DELAY, null)).isEmpty();
    }

    @Test
    void resolve_unknown_notFound() {
        assertThatThrownBy(() -> useCase.resolve(999_999L)).isInstanceOf(IncidentNotFoundException.class);
    }
}
