package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.OrderLine;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.domain.exception.ClientesUnavailableException;
import com.example.mspedidos.domain.exception.DuplicateExternalOrderException;
import com.example.mspedidos.domain.exception.PersonNotFoundException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class OrderServiceTest extends ServiceTestSupport {

    @Autowired
    OrderUseCase orderUseCase;

    @Test
    void create_8units_holderGets3_rest5UnassignedWithIncidents() {
        stubHolder(1L, dni("111"));

        OrderView view = orderUseCase.create(command(1L, "A-1", ORDER_DATE, line(8, "100.00")));

        List<OrderItem> items = itemRepository.findByOrderId(view.order().getId());
        assertThat(items).hasSize(8).allSatisfy(i -> {
            assertThat(i.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
            assertThat(i.getSku()).isEqualTo("SKU-1");
            assertThat(i.getUnitPrice()).isEqualByComparingTo("100.00");
        });
        // en el orden recibido: las 3 primeras son del titular
        assertThat(items.subList(0, 3)).allSatisfy(i -> {
            assertThat(i.getImporterType()).isEqualTo(ImporterType.HOLDER);
            assertThat(i.getImporterDocument()).isEqualTo(dni("111"));
            assertThat(historyCount(i.getId(), HistoryEventType.IMPORTER_ASSIGNED)).isEqualTo(1);
            assertThat(incidentRepository.findOpen(i.getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING)).isEmpty();
        });
        assertThat(items.subList(3, 8)).allSatisfy(i -> {
            assertThat(i.getImporterType()).isEqualTo(ImporterType.UNASSIGNED);
            assertThat(i.getImporterDocument()).isNull();
            assertThat(incidentRepository.findOpen(i.getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING))
                    .get().extracting(ItemIncident::getOpenedBy).isEqualTo("op-1");
            assertThat(historyCount(i.getId(), HistoryEventType.INCIDENT_OPENED)).isEqualTo(1);
        });
        assertThat(incidentRepository.search(IncidentStatus.OPEN, IncidentType.IMPORTER_ASSIGNMENT_PENDING, null))
                .hasSize(5);
        assertThat(items).allSatisfy(i -> {
            List<OrderItemHistoryEntry> created = historyRepository.findByOrderItemId(i.getId()).stream()
                    .filter(h -> h.eventType() == HistoryEventType.CREATED).toList();
            assertThat(created).singleElement().satisfies(h -> {
                assertThat(h.toValue()).isEqualTo("PROCESSING");
                assertThat(h.performedBy()).isEqualTo("op-1");
            });
        });
        OrderItemHistoryEntry assigned = historyRepository.findByOrderItemId(items.get(0).getId()).stream()
                .filter(h -> h.eventType() == HistoryEventType.IMPORTER_ASSIGNED).findFirst().orElseThrow();
        assertThat(assigned.fromValue()).isEqualTo("UNASSIGNED");
        assertThat(assigned.toValue()).isEqualTo("HOLDER:111");

        assertThat(view.items()).hasSize(8);
        assertThat(view.order().getItemsTotal()).isEqualByComparingTo("800.00");
        assertThat(orderRepository.findById(view.order().getId()).orElseThrow().getItemsTotal())
                .isEqualByComparingTo("800.00");
    }

    @Test
    void create_multipleLines_expandedInReceivedOrder() {
        stubHolder(1L, dni("111"));
        OrderLine first = new OrderLine("SKU-A", "L-A", 2, new BigDecimal("5.00"));
        OrderLine second = new OrderLine("SKU-B", "L-B", 2, new BigDecimal("7.50"));

        OrderView view = orderUseCase.create(command(1L, "A-ML", ORDER_DATE, first, second));

        List<OrderItem> items = itemRepository.findByOrderId(view.order().getId());
        assertThat(items).extracting(OrderItem::getSku).containsExactly("SKU-A", "SKU-A", "SKU-B", "SKU-B");
        assertThat(items).extracting(OrderItem::getImporterType).containsExactly(
                ImporterType.HOLDER, ImporterType.HOLDER, ImporterType.HOLDER, ImporterType.UNASSIGNED);
        assertThat(view.order().getItemsTotal()).isEqualByComparingTo("25.00");
    }

    @Test
    void create_holderAlreadyUsed2_onlyOneAssigned() {
        stubHolder(1L, dni("111"));
        seedUsedUnits(dni("111"), ImporterType.FAMILY_MEMBER, ORDER_DATE.minusMonths(1), 2);

        OrderView view = orderUseCase.create(command(1L, "A-2", ORDER_DATE, line(3, "10.00")));

        assertThat(itemRepository.findByOrderId(view.order().getId())).extracting(OrderItem::getImporterType)
                .containsExactly(ImporterType.HOLDER, ImporterType.UNASSIGNED, ImporterType.UNASSIGNED);
    }

    @Test
    void create_duplicateExternalOrder_conflict() {
        stubHolder(1L, dni("111"));
        orderUseCase.create(command(1L, "DUP-1", ORDER_DATE, line(2, "10.00")));
        long items = itemCount();

        assertThatThrownBy(() -> orderUseCase.create(command(1L, "DUP-1", ORDER_DATE, line(2, "10.00"))))
                .isInstanceOf(DuplicateExternalOrderException.class);

        assertThat(itemCount()).isEqualTo(items);
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void create_holderNotFound_persistsNothing() {
        when(clientesPort.getHolder(5L)).thenThrow(new PersonNotFoundException("titular 5"));

        assertThatThrownBy(() -> orderUseCase.create(command(5L, "NF-1", ORDER_DATE, line(2, "10.00"))))
                .isInstanceOf(PersonNotFoundException.class);

        assertThat(orderCount()).isZero();
        assertThat(itemCount()).isZero();
    }

    @Test
    void create_clientesDown_persistsNothing() {
        when(clientesPort.getHolder(5L)).thenThrow(new ClientesUnavailableException("down", null));

        assertThatThrownBy(() -> orderUseCase.create(command(5L, "DOWN-1", ORDER_DATE, line(2, "10.00"))))
                .isInstanceOf(ClientesUnavailableException.class);

        assertThat(orderCount()).isZero();
        assertThat(itemCount()).isZero();
    }
}
