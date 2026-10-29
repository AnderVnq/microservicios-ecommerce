package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.domain.model.EntryType;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_units",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_units_allocated_item",
                columnNames = "allocated_order_item_id"),
        indexes = {
                @Index(name = "idx_inventory_units_sku_status_wh", columnList = "sku, status, warehouse"),
                @Index(name = "idx_inventory_units_entry", columnList = "entry_id"),
                @Index(name = "idx_inventory_units_invoice", columnList = "invoice_number"),
                @Index(name = "idx_inventory_units_purchase", columnList = "purchase_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryUnitJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", nullable = false)
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condition_code", nullable = false)
    private ItemConditionJpaEntity condition;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "warehouse", nullable = false, length = 40)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "status", nullable = false, length = 40)
    private InventoryUnitStatus status;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "cost_currency", nullable = false, length = 3)
    private String costCurrency;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "entry_type", nullable = false, length = 40)
    private EntryType entryType;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "supplier_purchase_id")
    private String supplierPurchaseId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "expected_arrival_date")
    private LocalDate expectedArrivalDate;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "supplier_return_deadline")
    private LocalDate supplierReturnDeadline;

    @Column(name = "allocated_order_item_id")
    private Long allocatedOrderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private InventoryEntryJpaEntity entry;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
