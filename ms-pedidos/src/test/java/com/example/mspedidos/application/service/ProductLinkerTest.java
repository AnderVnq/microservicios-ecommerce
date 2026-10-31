package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderLine;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.domain.exception.ProductsUnavailableException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ProductLinkerTest extends ServiceTestSupport {

    @Autowired
    ProductLinker linker;
    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;

    /** Orden del market 1 cuyas lineas no se resuelven: todas las unidades quedan pendientes de vinculo. */
    private OrderView pendingOrder(OrderLine... lines) {
        stubHolder(1L, dni("111"));
        when(productsPort.resolve(any(), any())).thenReturn(Map.of());
        return orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, lines));
    }

    private static OrderLine marketLine(String marketSku, int quantity) {
        return new OrderLine(marketSku, "E-" + marketSku, quantity, new BigDecimal("10.00"));
    }

    private OrderItem reload(OrderItem item) {
        return itemRepository.findById(item.getId()).orElseThrow();
    }

    private List<OrderItemHistoryEntry> events(Long itemId, HistoryEventType type) {
        return historyRepository.findByOrderItemId(itemId).stream().filter(h -> h.eventType() == type).toList();
    }

    @Test
    void linker_linksPendingAndClosesIncident() {
        OrderView view = pendingOrder(marketLine("FAL-1", 2));
        when(productsPort.resolve(eq(1L), any())).thenReturn(Map.of("FAL-1", "ZAP-1"));

        int linked = linker.linkPending();

        assertThat(linked).isEqualTo(2);
        for (OrderItem unit : view.items()) {
            OrderItem stored = reload(unit);
            assertThat(stored.getSku()).isEqualTo("ZAP-1");
            assertThat(stored.getMarketSku()).isEqualTo("FAL-1");
            assertThat(incidentRepository.findOpen(unit.getId(), IncidentType.PRODUCT_LINK_PENDING)).isEmpty();
            OrderItemHistoryEntry link = events(unit.getId(), HistoryEventType.SKU_LINKED).get(0);
            assertThat(link.fromValue()).isNull();
            assertThat(link.toValue()).isEqualTo("ZAP-1");
            assertThat(link.performedBy()).isEqualTo("system");
            OrderItemHistoryEntry resolved = events(unit.getId(), HistoryEventType.INCIDENT_RESOLVED).get(0);
            assertThat(resolved.fromValue()).isEqualTo(IncidentType.PRODUCT_LINK_PENDING.name());
            assertThat(resolved.performedBy()).isEqualTo("system");
        }
        List<ItemIncident> closed = incidentRepository.search(IncidentStatus.RESOLVED,
                IncidentType.PRODUCT_LINK_PENDING, null);
        assertThat(closed).hasSize(2).allSatisfy(i -> assertThat(i.getResolvedBy()).isEqualTo("system"));
        // segunda vuelta: ya no hay nada pendiente
        assertThat(linker.linkPending()).isZero();
    }

    @Test
    void linker_unresolvedStaysPending() {
        OrderView view = pendingOrder(marketLine("FAL-1", 1), marketLine("FAL-2", 1));
        OrderItem first = view.items().stream().filter(i -> i.getMarketSku().equals("FAL-1")).findFirst().orElseThrow();
        OrderItem second = view.items().stream().filter(i -> i.getMarketSku().equals("FAL-2")).findFirst().orElseThrow();
        when(productsPort.resolve(eq(1L), any())).thenReturn(Map.of("FAL-1", "ZAP-1"));

        assertThat(linker.linkPending()).isEqualTo(1);

        assertThat(reload(first).getSku()).isEqualTo("ZAP-1");
        assertThat(reload(second).getSku()).isNull();
        assertThat(incidentRepository.findOpen(second.getId(), IncidentType.PRODUCT_LINK_PENDING)).isPresent();
        assertThat(historyCount(second.getId(), HistoryEventType.SKU_LINKED)).isZero();
    }

    @Test
    void linker_productsDown_noChange() {
        OrderView view = pendingOrder(marketLine("FAL-1", 1));
        OrderItem unit = view.items().get(0);
        when(productsPort.resolve(any(), any())).thenThrow(new ProductsUnavailableException("caido"));

        assertThat(linker.linkPending()).isZero();

        assertThat(reload(unit).getSku()).isNull();
        assertThat(incidentRepository.findOpen(unit.getId(), IncidentType.PRODUCT_LINK_PENDING)).isPresent();
        assertThat(historyCount(unit.getId(), HistoryEventType.SKU_LINKED)).isZero();
    }

    @Test
    void linker_productsUnexpectedError_noChange_noException() {
        OrderView view = pendingOrder(marketLine("FAL-1", 1));
        OrderItem unit = view.items().get(0);
        when(productsPort.resolve(any(), any())).thenThrow(new IllegalStateException("ms-products rechazo"));

        // el job no aborta ni POST /link-skus responde 500: no cambia nada y se reintenta en la siguiente vuelta
        assertThat(linker.linkPending()).isZero();

        assertThat(reload(unit).getSku()).isNull();
        assertThat(incidentRepository.findOpen(unit.getId(), IncidentType.PRODUCT_LINK_PENDING)).isPresent();
        assertThat(historyCount(unit.getId(), HistoryEventType.SKU_LINKED)).isZero();
    }

    @Test
    void linker_skipsUnitLinkedManuallyMeanwhile() {
        OrderView view = pendingOrder(marketLine("FAL-1", 1));
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        // mientras se consulta el catalogo un operador enlaza la unidad a mano
        when(productsPort.resolve(eq(1L), any())).thenAnswer(inv -> {
            itemUseCase.updateDetails(orderId, unit.getId(), "MANUAL", null, unit.getExternalItemId(),
                    unit.getUnitPrice());
            return Map.of("FAL-1", "ZAP-1");
        });

        assertThat(linker.linkPending()).isZero();

        assertThat(reload(unit).getSku()).isEqualTo("MANUAL");
        List<OrderItemHistoryEntry> links = events(unit.getId(), HistoryEventType.SKU_LINKED);
        assertThat(links).hasSize(1);
        assertThat(links.get(0).performedBy()).isEqualTo("op-1");
        assertThat(historyCount(unit.getId(), HistoryEventType.INCIDENT_RESOLVED)).isEqualTo(1);
    }

    @Test
    void linker_ignoresCancelledUnits() {
        OrderView view = pendingOrder(marketLine("FAL-1", 1));
        OrderItem unit = view.items().get(0);
        itemUseCase.changeStatus(view.order().getId(), unit.getId(), OrderItemStatus.CANCELLED, null, null);
        when(productsPort.resolve(eq(1L), any())).thenReturn(Map.of("FAL-1", "ZAP-1"));

        assertThat(linker.linkPending()).isZero();

        assertThat(reload(unit).getSku()).isNull();
        assertThat(historyCount(unit.getId(), HistoryEventType.SKU_LINKED)).isZero();
        // al cancelar se cerro su incidencia de vinculo (no queda abierta para siempre)
        assertThat(incidentRepository.findOpen(unit.getId(), IncidentType.PRODUCT_LINK_PENDING)).isEmpty();
    }

    @Test
    void linker_resolvesPerMarket() {
        // mismo SKU de market en dos markets distintos: cada uno se resuelve con su propia equivalencia
        Order market1 = seedOrder(1L, ORDER_DATE);
        Order market2 = orderRepository.save(Order.builder().orderNumber("SEED").externalOrderId(uniqueExternalId())
                .marketId(2L).holderId(1L).orderDate(ORDER_DATE).itemsTotal(BigDecimal.ZERO).build());
        OrderItem a = itemRepository.save(pendingUnit(market1, "SHARED"));
        OrderItem b = itemRepository.save(pendingUnit(market2, "SHARED"));
        when(productsPort.resolve(eq(1L), any())).thenReturn(Map.of("SHARED", "ZAP-M1"));
        when(productsPort.resolve(eq(2L), any())).thenReturn(Map.of("SHARED", "ZAP-M2"));

        assertThat(linker.linkPending()).isEqualTo(2);

        assertThat(reload(a).getSku()).isEqualTo("ZAP-M1");
        assertThat(reload(b).getSku()).isEqualTo("ZAP-M2");
    }

    private static OrderItem pendingUnit(Order order, String marketSku) {
        OrderItem fresh = OrderItem.newUnit(marketSku, null, "EXT", BigDecimal.TEN);
        return OrderItem.builder().orderId(order.getId()).marketSku(marketSku).externalItemId("EXT")
                .unitPrice(BigDecimal.TEN).status(fresh.getStatus()).importerType(fresh.getImporterType()).build();
    }
}
