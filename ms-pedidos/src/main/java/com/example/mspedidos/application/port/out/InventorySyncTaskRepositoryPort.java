package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.InventorySyncTask;

import java.util.List;
import java.util.Optional;

public interface InventorySyncTaskRepositoryPort {

    InventorySyncTask save(InventorySyncTask task);

    /** Tareas {@code PENDING} por id ascendente. */
    List<InventorySyncTask> findPendingOrdered();

    /** Todas las tareas de la compra por id ascendente. */
    List<InventorySyncTask> findByPurchaseId(Long purchaseId);

    /** Con lock pesimista de escritura; requiere transaccion. */
    Optional<InventorySyncTask> findByIdForUpdate(Long id);
}
