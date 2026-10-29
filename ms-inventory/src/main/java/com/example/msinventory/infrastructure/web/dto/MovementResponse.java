package com.example.msinventory.infrastructure.web.dto;

import com.example.msinventory.domain.model.InventoryMovement;
import com.example.msinventory.domain.model.MovementType;
import com.example.msinventory.domain.model.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;

public record MovementResponse(Long id, Long unitId, MovementType type, String sku, String conditionCode,
                               BigDecimal unitCost, String costCurrency, Warehouse fromWarehouse,
                               Warehouse toWarehouse, String reference, String reason, String performedBy,
                               Instant occurredAt) {

    public static MovementResponse from(InventoryMovement m) {
        return new MovementResponse(m.id(), m.unitId(), m.type(), m.sku(), m.conditionCode(), m.unitCost(),
                m.costCurrency(), m.fromWarehouse(), m.toWarehouse(), m.reference(), m.reason(),
                m.performedBy(), m.occurredAt());
    }
}
