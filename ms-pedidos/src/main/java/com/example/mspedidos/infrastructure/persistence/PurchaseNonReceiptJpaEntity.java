package com.example.mspedidos.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "purchase_non_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_non_receipts_key", columnNames = "idempotency_key"),
        indexes = @Index(name = "idx_purchase_non_receipts_purchase", columnList = "purchase_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseNonReceiptJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "order_item_ids", nullable = false, columnDefinition = "text")
    private String orderItemIds;

    @Column(name = "surplus_quantity", nullable = false)
    private int surplusQuantity;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "status_change_request_id")
    private Long statusChangeRequestId;

    @Column(name = "applied_by", nullable = false)
    private String appliedBy;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    /** Solo marcas directas (sobrante); null si vino de una solicitud. */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @PrePersist
    void onCreate() {
        if (appliedAt == null) {
            appliedAt = Instant.now();
        }
    }
}
