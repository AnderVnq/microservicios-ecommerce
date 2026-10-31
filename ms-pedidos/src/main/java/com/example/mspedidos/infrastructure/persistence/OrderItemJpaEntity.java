package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.DeliveredTo;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Warehouse;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order", columnList = "order_id"),
        @Index(name = "idx_order_items_purchase", columnList = "purchase_id"),
        @Index(name = "idx_order_items_importer_doc", columnList = "importer_document_type, importer_document_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column
    private String sku;

    @Column(name = "market_sku", length = 255)
    private String marketSku;

    @Column(name = "external_item_id")
    private String externalItemId;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(nullable = false, length = 40)
    private OrderItemStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(length = 40)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "delivered_to", length = 40)
    private DeliveredTo deliveredTo;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "importer_type", nullable = false, length = 40)
    private ImporterType importerType;

    @Column(name = "family_member_id")
    private Long familyMemberId;

    @Column(name = "importer_document_type", length = 20)
    private String importerDocumentType;

    @Column(name = "importer_document_number", length = 40)
    private String importerDocumentNumber;

    @Column(name = "inventory_unit_id")
    private Long inventoryUnitId;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Builder.Default
    @Column(name = "inventory_release_pending", nullable = false, columnDefinition = "boolean not null default false")
    private boolean inventoryReleasePending = false;

    @Column(name = "pending_release_unit_id")
    private Long pendingReleaseUnitId;

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
