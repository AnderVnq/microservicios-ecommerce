package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.domain.exception.ImporterRequiredException;
import com.example.mspedidos.domain.exception.ItemNotEditableException;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderHeaderChanges;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemLifecycleTest extends ServiceTestSupport {

    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;

    /** Orden del titular 1 con 4 unidades de 10.00: 3 HOLDER y la ultima UNASSIGNED con incidencia. */
    private OrderView orderWithFourUnits() {
        stubHolder(1L, dni("111"));
        return orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(4, "10.00")));
    }

    @Test
    void changeStatus_writesHistoryFromTo() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);

        OrderItem result = itemUseCase.changeStatus(view.order().getId(), unit.getId(),
                OrderItemStatus.PURCHASED, null, null);

        assertThat(result.getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(itemRepository.findByIdAndOrderId(unit.getId(), view.order().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderItemStatus.PURCHASED);
        OrderItemHistoryEntry last = historyRepository.findByOrderItemId(unit.getId()).stream()
                .filter(h -> h.eventType() == HistoryEventType.STATUS_CHANGED).findFirst().orElseThrow();
        assertThat(last.fromValue()).isEqualTo("PROCESSING");
        assertThat(last.toValue()).isEqualTo("PURCHASED");
        assertThat(last.performedBy()).isEqualTo("op-1");
    }

    @Test
    void changeStatus_unassignedToReadyToShip_conflict() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unassigned = view.items().stream()
                .filter(i -> i.getImporterType() == com.example.mspedidos.domain.model.ImporterType.UNASSIGNED)
                .findFirst().orElseThrow();
        itemUseCase.changeStatus(orderId, unassigned.getId(), OrderItemStatus.PURCHASED, null, null);
        itemUseCase.changeStatus(orderId, unassigned.getId(), OrderItemStatus.RECEIVED_AT_WAREHOUSE,
                com.example.mspedidos.domain.model.Warehouse.USA, null);

        assertThatThrownBy(() -> itemUseCase.changeStatus(orderId, unassigned.getId(),
                OrderItemStatus.READY_TO_SHIP, null, null)).isInstanceOf(ImporterRequiredException.class);

        assertThat(itemRepository.findByIdAndOrderId(unassigned.getId(), orderId).orElseThrow().getStatus())
                .isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
    }

    @Test
    void cancelOrder_cancelsOnlyNonTerminalUnits_andRecalcsTotal() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem alreadyCancelled = view.items().get(0);
        itemUseCase.changeStatus(orderId, alreadyCancelled.getId(), OrderItemStatus.CANCELLED, null, null);
        assertThat(orderRepository.findById(orderId).orElseThrow().getItemsTotal()).isEqualByComparingTo("30.00");

        OrderView result = orderUseCase.cancel(orderId);

        assertThat(result.items()).hasSize(4).extracting(OrderItem::getStatus)
                .containsOnly(OrderItemStatus.CANCELLED);
        assertThat(result.order().getItemsTotal()).isEqualByComparingTo("0.00");
        assertThat(orderRepository.findById(orderId).orElseThrow().getItemsTotal()).isEqualByComparingTo("0.00");
        assertThat(historyCount(alreadyCancelled.getId(), HistoryEventType.CANCELLED)).isEqualTo(1);
        OrderItem other = view.items().get(1);
        List<OrderItemHistoryEntry> history = historyRepository.findByOrderItemId(other.getId());
        OrderItemHistoryEntry cancelled = history.stream()
                .filter(h -> h.eventType() == HistoryEventType.CANCELLED).findFirst().orElseThrow();
        assertThat(cancelled.fromValue()).isEqualTo("PROCESSING");
        assertThat(cancelled.toValue()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelOrder_leavesCustomerReceivedUntouched() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);
        OrderItem received = OrderItem.builder().id(unit.getId()).orderId(unit.getOrderId()).sku(unit.getSku())
                .externalItemId(unit.getExternalItemId()).unitPrice(unit.getUnitPrice())
                .status(OrderItemStatus.CUSTOMER_RECEIVED).importerType(unit.getImporterType())
                .importerDocument(unit.getImporterDocument()).build();
        itemRepository.save(received);

        OrderView result = orderUseCase.cancel(view.order().getId());

        assertThat(itemRepository.findByIdAndOrderId(unit.getId(), view.order().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderItemStatus.CUSTOMER_RECEIVED);
        assertThat(historyCount(unit.getId(), HistoryEventType.CANCELLED)).isZero();
        // la unidad CUSTOMER_RECEIVED sigue contando en el total
        assertThat(result.order().getItemsTotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void cancelUnit_resolvesPendingIncident() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(3);
        ItemIncident incident = incidentRepository.findOpen(unit.getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING)
                .orElseThrow();

        itemUseCase.changeStatus(view.order().getId(), unit.getId(), OrderItemStatus.CANCELLED, null, null);

        ItemIncident resolved = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedBy()).isEqualTo("op-1");
        assertThat(historyCount(unit.getId(), HistoryEventType.CANCELLED)).isEqualTo(1);
        assertThat(historyCount(unit.getId(), HistoryEventType.INCIDENT_RESOLVED)).isEqualTo(1);
    }

    @Test
    void updateDetails_recalcsItemsTotal() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);

        OrderItem result = itemUseCase.updateDetails(view.order().getId(), unit.getId(), "SKU-2", "EXT-2",
                new BigDecimal("25.00"));

        assertThat(result.getSku()).isEqualTo("SKU-2");
        assertThat(result.getUnitPrice()).isEqualByComparingTo("25.00");
        assertThat(orderRepository.findById(view.order().getId()).orElseThrow().getItemsTotal())
                .isEqualByComparingTo("55.00");
        assertThat(historyCount(unit.getId(), HistoryEventType.UPDATED)).isEqualTo(1);
    }

    @Test
    void updateDetails_afterPurchased_conflict() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);
        itemUseCase.changeStatus(view.order().getId(), unit.getId(), OrderItemStatus.PURCHASED, null, null);

        assertThatThrownBy(() -> itemUseCase.updateDetails(view.order().getId(), unit.getId(), "X", "Y",
                BigDecimal.ONE)).isInstanceOf(ItemNotEditableException.class);
        assertThat(historyCount(unit.getId(), HistoryEventType.UPDATED)).isZero();
    }

    @Test
    void history_returnsChronological() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        itemUseCase.changeStatus(orderId, unit.getId(), OrderItemStatus.PURCHASED, null, null);
        itemUseCase.changeStatus(orderId, unit.getId(), OrderItemStatus.CANCELLED, null, null);

        List<OrderItemHistoryEntry> history = itemUseCase.history(orderId, unit.getId());

        assertThat(history).extracting(OrderItemHistoryEntry::eventType).containsSubsequence(
                HistoryEventType.CREATED, HistoryEventType.STATUS_CHANGED, HistoryEventType.CANCELLED);
        assertThat(history).extracting(OrderItemHistoryEntry::occurredAt).isSorted();
    }

    @Test
    void findOne_itemOfOtherOrder_notFound() {
        OrderView a = orderWithFourUnits();
        stubHolder(2L, dni("222"));
        OrderView b = orderUseCase.create(command(2L, uniqueExternalId(), ORDER_DATE, line(1, "10.00")));

        assertThatThrownBy(() -> itemUseCase.findOne(b.order().getId(), a.items().get(0).getId()))
                .isInstanceOf(OrderItemNotFoundException.class);
        assertThatThrownBy(() -> itemUseCase.history(b.order().getId(), a.items().get(0).getId()))
                .isInstanceOf(OrderItemNotFoundException.class);
        assertThat(itemUseCase.findOne(a.order().getId(), a.items().get(0).getId()).getId())
                .isEqualTo(a.items().get(0).getId());
        assertThat(itemUseCase.findByOrder(a.order().getId())).hasSize(4);
    }

    @Test
    void updateHeader_changesHeaderOnly_andFindById() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        Order o = view.order();

        Order updated = orderUseCase.updateHeader(orderId, new OrderHeaderChanges("ORD-NEW", o.getShippingDeadline(),
                o.getUsaDepartureDeadline(), null, 5, "SEA", new BigDecimal("1.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, "nota"));

        assertThat(updated.getOrderNumber()).isEqualTo("ORD-NEW");
        OrderView found = orderUseCase.findById(orderId);
        assertThat(found.order().getShippingMethod()).isEqualTo("SEA");
        assertThat(found.order().getItemsTotal()).isEqualByComparingTo("40.00");
        assertThat(found.items()).hasSize(4);
        assertThat(orderUseCase.findAll()).hasSize(1);
    }
}
