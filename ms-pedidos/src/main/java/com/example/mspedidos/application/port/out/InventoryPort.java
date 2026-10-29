package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.Warehouse;

import java.math.BigDecimal;

/** Puerto hacia ms-inventory. Nunca debe invocarse mientras se mantiene un lock de BD. */
public interface InventoryPort {

    AllocatedUnit allocate(Long unitId, Long orderItemId, String sku);

    void release(Long unitId, Long orderItemId);

    record AllocatedUnit(Long unitId, Warehouse warehouse, String conditionCode, BigDecimal unitCost) {
    }
}
