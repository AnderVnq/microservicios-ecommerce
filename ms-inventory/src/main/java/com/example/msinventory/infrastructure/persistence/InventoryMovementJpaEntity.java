package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.domain.model.Direction;
import com.example.msinventory.domain.model.MovementType;
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

@Entity
@Table(name = "inventory_movements", indexes = {
        @Index(name = "idx_inventory_movements_sku_date", columnList = "sku, occurred_at"),
        @Index(name = "idx_inventory_movements_unit", columnList = "inventory_unit_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_unit_id", nullable = false)
    private Long unitId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "movement_type", nullable = false, length = 40)
    private MovementType type;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "direction", nullable = false, length = 40)
    private Direction direction;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "condition_code", nullable = false, length = 30)
    private String conditionCode;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "cost_currency", nullable = false, length = 3)
    private String costCurrency;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "from_warehouse", length = 40)
    private Warehouse fromWarehouse;

    @Enumerated(EnumType.STRING)
    @JdbcType(PlainVarcharJdbcType.class)
    @Column(name = "to_warehouse", length = 40)
    private Warehouse toWarehouse;

    @Column(name = "reference")
    private String reference;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
