package com.example.mspedidos.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", uniqueConstraints =
        @UniqueConstraint(name = "uk_orders_market_external", columnNames = {"market_id", "external_order_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "external_order_id", nullable = false)
    private String externalOrderId;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "holder_id", nullable = false)
    private Long holderId;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "shipping_deadline")
    private LocalDateTime shippingDeadline;

    @Column(name = "usa_departure_deadline")
    private LocalDateTime usaDepartureDeadline;

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @Column(name = "lead_time_to_ship")
    private Integer leadTimeToShip;

    @Column(name = "shipping_method")
    private String shippingMethod;

    @Column(name = "items_total", precision = 12, scale = 2)
    private BigDecimal itemsTotal;

    @Column(name = "shipping_cost", precision = 12, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "order_expenses", precision = 12, scale = 2)
    private BigDecimal orderExpenses;

    @Column(name = "store_discount_total", precision = 12, scale = 2)
    private BigDecimal storeDiscountTotal;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
