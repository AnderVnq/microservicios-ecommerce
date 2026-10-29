package com.example.msinventory.application.service;

import com.example.msinventory.application.port.out.InventoryMovementRepositoryPort;
import com.example.msinventory.domain.model.InventoryMovement;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.MovementType;
import com.example.msinventory.domain.model.Warehouse;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Escribe el movimiento de kardex de una unidad (sku/condicion/costo copiados de la unidad).
 * Convencion de almacenes: entradas, ARRIVAL y ALLOCATION_RELEASED -> to = almacen de la unidad;
 * salidas -> from = almacen de la unidad; TRANSFER -> ambos. El llamador pasa los valores correctos.
 * Debe invocarse dentro de la misma transaccion que el cambio de la unidad.
 */
@Component
public class MovementRecorder {

    private final InventoryMovementRepositoryPort movements;

    public MovementRecorder(InventoryMovementRepositoryPort movements) {
        this.movements = movements;
    }

    public void record(InventoryUnit unit, MovementType type, Warehouse from, Warehouse to,
                       String reference, String reason, String actor) {
        movements.append(new InventoryMovement(null, unit.getId(), type, unit.getSku(), unit.getConditionCode(),
                unit.getUnitCost(), unit.getCostCurrency(), from, to, reference, reason, actor, Instant.now()));
    }
}
