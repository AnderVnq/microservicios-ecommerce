package com.example.msinventory.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryMovement(
        Long id,
        Long unitId,
        MovementType type,
        String sku,
        String conditionCode,
        BigDecimal unitCost,
        String costCurrency,
        Warehouse fromWarehouse,
        Warehouse toWarehouse,
        String reference,
        String reason,
        String performedBy,
        Instant occurredAt) {
}
