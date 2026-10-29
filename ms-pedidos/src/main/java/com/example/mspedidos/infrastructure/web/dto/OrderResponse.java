package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.domain.model.Order;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** {@code items} es null (y se omite) cuando la respuesta es solo la cabecera. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        Long id,
        String orderNumber,
        String externalOrderId,
        Long marketId,
        Long holderId,
        LocalDateTime orderDate,
        LocalDateTime shippingDeadline,
        LocalDateTime usaDepartureDeadline,
        LocalDateTime deliveryDate,
        Integer leadTimeToShip,
        String shippingMethod,
        BigDecimal itemsTotal,
        BigDecimal shippingCost,
        BigDecimal orderExpenses,
        BigDecimal storeDiscountTotal,
        String note,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order o) {
        return from(o, null);
    }

    public static OrderResponse from(OrderView view) {
        return from(view.order(), view.items().stream().map(OrderItemResponse::from).toList());
    }

    private static OrderResponse from(Order o, List<OrderItemResponse> items) {
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getExternalOrderId(), o.getMarketId(),
                o.getHolderId(), o.getOrderDate(), o.getShippingDeadline(), o.getUsaDepartureDeadline(),
                o.getDeliveryDate(), o.getLeadTimeToShip(), o.getShippingMethod(), o.getItemsTotal(),
                o.getShippingCost(), o.getOrderExpenses(), o.getStoreDiscountTotal(), o.getNote(),
                o.getCreatedAt(), o.getUpdatedAt(), items);
    }
}
