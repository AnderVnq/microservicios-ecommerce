package com.example.msinventory.application.port.out;

import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryUnitRepositoryPort {

    InventoryUnit save(InventoryUnit unit);

    List<InventoryUnit> saveAll(List<InventoryUnit> units);

    Optional<InventoryUnit> findById(Long id);

    /** Bloqueo PESSIMISTIC_WRITE; requiere transaccion activa. */
    Optional<InventoryUnit> findByIdForUpdate(Long id);

    List<InventoryUnit> findByEntryId(Long entryId);

    List<InventoryUnit> search(UnitFilter filter);

    Optional<InventoryUnit> findByAllocatedOrderItemId(Long orderItemId);

    /** Filtros opcionales (null = cualquiera). {@code returnDeadlineBefore} aplica a supplier_return_deadline &lt; valor. */
    record UnitFilter(String sku, InventoryUnitStatus status, Warehouse warehouse, String conditionCode,
                      LocalDate returnDeadlineBefore) {
    }
}
