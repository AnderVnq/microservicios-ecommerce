package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderLine;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.out.ProductsPort;
import com.example.mspedidos.domain.exception.ProductsUnavailableException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.infrastructure.web.dto.OrderItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class OrderSkuResolutionTest extends ServiceTestSupport {

    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;

    @Test
    void create_resolvedUnitsGetInternalSku_marketSkuKept() {
        stubHolder(1L, dni("111"));
        when(productsPort.resolve(eq(1L), any())).thenReturn(Map.of("FAL-1", "ZAP-X-42"));

        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE,
                new OrderLine("FAL-1", "E", 2, new BigDecimal("10.00"))));

        List<OrderItem> items = itemRepository.findByOrderId(view.order().getId());
        assertThat(items).hasSize(2).allSatisfy(i -> {
            assertThat(i.getSku()).isEqualTo("ZAP-X-42");
            assertThat(i.getMarketSku()).isEqualTo("FAL-1");
            assertThat(i.isProductLinkPending()).isFalse();
            assertThat(incidentRepository.findOpen(i.getId(), IncidentType.PRODUCT_LINK_PENDING)).isEmpty();
        });
    }

    @Test
    void create_unresolved_pendingWithIncident() {
        stubHolder(1L, dni("111"));
        when(productsPort.resolve(eq(1L), any())).thenReturn(Map.of());

        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE,
                new OrderLine("FAL-9", "E", 1, new BigDecimal("10.00"))));

        OrderItem item = itemRepository.findByOrderId(view.order().getId()).get(0);
        assertThat(item.getSku()).isNull();
        assertThat(item.getMarketSku()).isEqualTo("FAL-9");
        assertThat(item.isProductLinkPending()).isTrue();
        ItemIncident incident = incidentRepository.findOpen(item.getId(), IncidentType.PRODUCT_LINK_PENDING)
                .orElseThrow();
        assertThat(incident.getScope()).isEqualTo(IncidentScope.INTERNAL);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getDescription()).isEqualTo("SKU del market sin equivalencia en el catalogo: FAL-9");
        assertThat(historyCount(item.getId(), HistoryEventType.INCIDENT_OPENED)).isEqualTo(1);
    }

    @Test
    void create_productsDown_orderCreatedAllPending() {
        stubHolder(1L, dni("111"));
        when(productsPort.resolve(any(), any())).thenThrow(new ProductsUnavailableException("caido"));

        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE,
                new OrderLine("FAL-1", "E", 2, new BigDecimal("10.00"))));

        assertThat(orderCount()).isEqualTo(1);
        assertThat(itemRepository.findByOrderId(view.order().getId()))
                .hasSize(2).allMatch(OrderItem::isProductLinkPending);
        assertThat(incidentRepository.search(IncidentStatus.OPEN, IncidentType.PRODUCT_LINK_PENDING, null))
                .hasSize(2);
    }

    @Test
    void create_productsUnexpectedError_orderCreatedAllPending() {
        stubHolder(1L, dni("111"));
        // p. ej. ms-products rechaza la peticion (4xx no transitorio): spec 8.2 "caido / error" -> pendiente
        when(productsPort.resolve(any(), any())).thenThrow(new IllegalStateException("ms-products rechazo"));

        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE,
                new OrderLine("FAL-1", "E", 2, new BigDecimal("10.00"))));

        assertThat(orderCount()).isEqualTo(1);
        assertThat(itemRepository.findByOrderId(view.order().getId()))
                .hasSize(2).allMatch(OrderItem::isProductLinkPending);
        assertThat(incidentRepository.search(IncidentStatus.OPEN, IncidentType.PRODUCT_LINK_PENDING, null))
                .hasSize(2);
    }

    @Test
    void addUnits_resolvesSameWay() {
        stubHolder(1L, dni("111"));
        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(1, "10.00")));
        when(productsPort.resolve(eq(1L), any())).thenReturn(Map.of("FAL-2", "ZAP-Y"));

        List<OrderItem> added = itemUseCase.addUnits(view.order().getId(),
                new OrderLine("FAL-2", "E2", 2, new BigDecimal("5.00")));

        assertThat(added).hasSize(2).allSatisfy(i -> {
            assertThat(i.getSku()).isEqualTo("ZAP-Y");
            assertThat(i.getMarketSku()).isEqualTo("FAL-2");
        });
    }

    @Test
    void response_includesMarketSku() {
        stubHolder(1L, dni("111"));
        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(1, "10.00")));

        OrderItemResponse response = OrderItemResponse.from(view.items().get(0));

        assertThat(response.marketSku()).isEqualTo("SKU-1");
        assertThat(response.sku()).isEqualTo("SKU-1");
    }
}
