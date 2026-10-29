package com.example.msinventory.application.port.out;

import com.example.msinventory.domain.model.InventoryMovement;

import java.time.Instant;
import java.util.List;

public interface InventoryMovementRepositoryPort {

    void append(InventoryMovement movement);

    List<InventoryMovement> findByUnitId(Long unitId);

    /** Ordenado por occurredAt, id. conditionCode null = cualquiera; to null = sin limite superior. */
    List<InventoryMovement> findForKardex(String sku, String conditionCode, String currency, Instant to);
}
