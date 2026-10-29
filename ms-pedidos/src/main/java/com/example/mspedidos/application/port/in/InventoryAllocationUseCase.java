package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.OrderItem;

public interface InventoryAllocationUseCase {

    /**
     * Cubre una unidad en {@code PROCESSING} con una unidad de inventario. Reintentar con la misma unidad de
     * inventario devuelve la unidad sin volver a llamar a ms-inventory.
     */
    OrderItem allocate(Long orderId, Long itemId, Long inventoryUnitId);

    /** Reintenta en ms-inventory la liberacion de cada unidad con {@code inventoryReleasePending}. */
    void retryPendingReleases();
}
