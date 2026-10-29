package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.InventoryUnit;

public interface AllocationUseCase {

    /** Idempotente: repetir con el mismo item devuelve la unidad sin escribir movimientos. */
    InventoryUnit allocate(Long unitId, Long orderItemId, String sku);

    /** No-op si la unidad ya esta libre. */
    void release(Long unitId, Long orderItemId);
}
