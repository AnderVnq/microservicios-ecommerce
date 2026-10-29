package com.example.mspedidos.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class Order {

    private Long id;
    private String orderNumber;
    private String externalOrderId;
    private Long marketId;
    private Long holderId;
    private LocalDateTime orderDate;
    private LocalDateTime shippingDeadline;
    private LocalDateTime usaDepartureDeadline;
    private LocalDateTime deliveryDate;
    private Integer leadTimeToShip;
    private String shippingMethod;
    private BigDecimal itemsTotal;
    private BigDecimal shippingCost;
    private BigDecimal orderExpenses;
    private BigDecimal storeDiscountTotal;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;

    /** Anio calendario del pedido; define el anio del cupo. */
    public int quotaYear() {
        return orderDate.getYear();
    }

    public void recalculateItemsTotal(List<OrderItem> items) {
        this.itemsTotal = items.stream()
                .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .map(OrderItem::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public void updateHeader(OrderHeaderChanges c) {
        this.orderNumber = c.orderNumber();
        this.shippingDeadline = c.shippingDeadline();
        this.usaDepartureDeadline = c.usaDepartureDeadline();
        this.deliveryDate = c.deliveryDate();
        this.leadTimeToShip = c.leadTimeToShip();
        this.shippingMethod = c.shippingMethod();
        this.shippingCost = c.shippingCost();
        this.orderExpenses = c.orderExpenses();
        this.storeDiscountTotal = c.storeDiscountTotal();
        this.note = c.note();
    }
}
