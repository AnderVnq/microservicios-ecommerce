package com.example.msinventory.application.port.in;

import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort.UnitFilter;
import com.example.msinventory.domain.model.InventoryMovement;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.Warehouse;

import java.time.LocalDate;
import java.util.List;

public interface InventoryUnitUseCase {

    /** Idempotente: si ya esta disponible no escribe movimiento. */
    InventoryUnit arrive(Long id);

    /**
     * Cada id en su propia transaccion (idempotente). Si algun id falla, los demas igual se procesan y
     * al final se relanza el primer error.
     */
    List<InventoryUnit> arriveAll(List<Long> ids);

    InventoryUnit transfer(Long id, Warehouse to, LocalDate expectedArrivalDate);

    /** {@code reason} opcional. */
    InventoryUnit supplierReturn(Long id, String reason);

    /** {@code reason} obligatorio. */
    InventoryUnit writeOff(Long id, String reason);

    /** {@code reason} obligatorio. */
    InventoryUnit adjustOut(Long id, String reason);

    /** Semantica PATCH: un parametro null deja esa fecha sin cambio. */
    InventoryUnit updateDates(Long id, LocalDate expectedArrivalDate, LocalDate supplierReturnDeadline);

    InventoryUnit findOne(Long id);

    List<InventoryUnit> search(UnitFilter filter);

    List<InventoryMovement> movements(Long id);
}
