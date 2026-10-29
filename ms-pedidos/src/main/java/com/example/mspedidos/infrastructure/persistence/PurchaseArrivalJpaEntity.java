package com.example.mspedidos.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "purchase_arrivals",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_arrivals_key", columnNames = "idempotency_key"),
        indexes = @Index(name = "idx_purchase_arrivals_purchase", columnList = "purchase_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseArrivalJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private int quantity;

    /** Ids de unidades separados por coma; cadena vacia si no hubo. */
    @Column(name = "order_item_ids", nullable = false, columnDefinition = "text")
    private String orderItemIds;

    @Column(name = "surplus_quantity", nullable = false)
    private int surplusQuantity;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
