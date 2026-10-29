package com.example.mspedidos.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private OrderItem item(String price, OrderItemStatus status) {
        return OrderItem.builder().unitPrice(new BigDecimal(price)).status(status).build();
    }

    @Test
    void itemsTotal_excludesCancelled() {
        Order order = Order.builder().build();

        order.recalculateItemsTotal(List.of(
                item("10.00", OrderItemStatus.PROCESSING),
                item("20.00", OrderItemStatus.DELIVERED),
                item("5.50", OrderItemStatus.CANCELLED)));

        assertThat(order.getItemsTotal()).isEqualByComparingTo("30.00");
        assertThat(order.getItemsTotal().scale()).isEqualTo(2);
    }

    @Test
    void itemsTotal_emptyList_isZero() {
        Order order = Order.builder().itemsTotal(new BigDecimal("99.00")).build();

        order.recalculateItemsTotal(List.of());

        assertThat(order.getItemsTotal()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void quotaYear_isCalendarYearOfOrderDate() {
        Order order = Order.builder().orderDate(LocalDateTime.of(2026, 12, 31, 23, 59)).build();

        assertThat(order.quotaYear()).isEqualTo(2026);
    }

    @Test
    void updateHeader_doesNotTouchHolderMarketDateOrExternalId() {
        LocalDateTime orderDate = LocalDateTime.of(2026, 1, 5, 10, 0);
        Instant created = Instant.parse("2026-01-05T10:00:00Z");
        Order order = Order.builder()
                .id(1L).orderNumber("OLD").externalOrderId("EXT-9").marketId(7L).holderId(3L)
                .orderDate(orderDate).createdAt(created)
                .itemsTotal(new BigDecimal("30.00")).note("old").build();

        LocalDateTime ship = LocalDateTime.of(2026, 2, 1, 0, 0);
        LocalDateTime usa = LocalDateTime.of(2026, 1, 20, 0, 0);
        LocalDateTime delivery = LocalDateTime.of(2026, 3, 1, 0, 0);
        order.updateHeader(new OrderHeaderChanges("NEW", ship, usa, delivery, 5, "AIR",
                new BigDecimal("4.00"), new BigDecimal("2.00"), new BigDecimal("1.00"), "new note"));

        assertThat(order.getOrderNumber()).isEqualTo("NEW");
        assertThat(order.getShippingDeadline()).isEqualTo(ship);
        assertThat(order.getUsaDepartureDeadline()).isEqualTo(usa);
        assertThat(order.getDeliveryDate()).isEqualTo(delivery);
        assertThat(order.getLeadTimeToShip()).isEqualTo(5);
        assertThat(order.getShippingMethod()).isEqualTo("AIR");
        assertThat(order.getShippingCost()).isEqualByComparingTo("4.00");
        assertThat(order.getOrderExpenses()).isEqualByComparingTo("2.00");
        assertThat(order.getStoreDiscountTotal()).isEqualByComparingTo("1.00");
        assertThat(order.getNote()).isEqualTo("new note");

        assertThat(order.getId()).isEqualTo(1L);
        assertThat(order.getExternalOrderId()).isEqualTo("EXT-9");
        assertThat(order.getMarketId()).isEqualTo(7L);
        assertThat(order.getHolderId()).isEqualTo(3L);
        assertThat(order.getOrderDate()).isEqualTo(orderDate);
        assertThat(order.getCreatedAt()).isEqualTo(created);
        assertThat(order.getItemsTotal()).isEqualByComparingTo("30.00");
    }
}
