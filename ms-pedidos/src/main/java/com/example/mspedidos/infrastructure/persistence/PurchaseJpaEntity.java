package com.example.mspedidos.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "purchases",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchases_idempotency_key", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "idx_purchases_supplier", columnList = "supplier_id"),
                @Index(name = "idx_purchases_sku", columnList = "sku"),
                @Index(name = "idx_purchases_invoice", columnList = "invoice_number"),
                @Index(name = "idx_purchases_supplier_purchase", columnList = "supplier_purchase_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "condition_code", nullable = false, length = 40)
    private String conditionCode;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "cost_currency", nullable = false, length = 3)
    private String costCurrency;

    @Column(name = "supplier_purchase_id", nullable = false, length = 100)
    private String supplierPurchaseId;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "expected_arrival_date", nullable = false)
    private LocalDate expectedArrivalDate;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    @Column(name = "not_received_quantity", nullable = false)
    private int notReceivedQuantity;

    @Column(name = "inventory_entry_id")
    private Long inventoryEntryId;

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
