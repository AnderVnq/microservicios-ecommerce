package com.example.msinventory.infrastructure.web.dto;

import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;

import java.math.BigDecimal;

/** Respuesta que consume ms-pedidos al asignar una unidad. */
public record AllocationResponse(Long unitId, String sku, Warehouse warehouse, String conditionCode,
                                 BigDecimal unitCost, String costCurrency, InventoryUnitStatus status,
                                 Long allocatedOrderItemId) {

    public static AllocationResponse from(InventoryUnit u) {
        return new AllocationResponse(u.getId(), u.getSku(), u.getWarehouse(), u.getConditionCode(),
                u.getUnitCost(), u.getCostCurrency(), u.getStatus(), u.getAllocatedOrderItemId());
    }
}
