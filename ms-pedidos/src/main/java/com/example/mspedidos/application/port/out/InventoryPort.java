package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.Warehouse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Puerto hacia ms-inventory. Nunca debe invocarse mientras se mantiene un lock de BD.
 * <p>
 * Operaciones de compras: un fallo transitorio (red, 5xx, token) lanza
 * {@link com.example.mspedidos.domain.exception.InventoryUnavailableException}; un rechazo definitivo (4xx) lanza
 * {@link com.example.mspedidos.domain.exception.InventorySyncRejectedException} con el codigo remoto.
 */
public interface InventoryPort {

    AllocatedUnit allocate(Long unitId, Long orderItemId, String sku);

    void release(Long unitId, Long orderItemId);

    /** Si la condicion existe y esta activa en ms-inventory; cualquier fallo lanza InventoryUnavailableException. */
    boolean isConditionActive(String code);

    /** Crea (o repite, con la misma clave) la entrada de la compra con sus unidades en camino y reservas. */
    PurchaseEntryResult createPurchaseEntry(String key, CreatePurchaseEntry body);

    void registerPurchaseArrival(String key, Long purchaseId, int quantity, List<Long> orderItemIds);

    void registerPurchaseNotReceived(String key, Long purchaseId, int quantity, List<Long> orderItemIds,
                                     String reason);

    record AllocatedUnit(Long unitId, Warehouse warehouse, String conditionCode, BigDecimal unitCost) {
    }

    /** Cuerpo de {@code POST /api/v1/inventory/purchase-entries}; tambien es el payload de la tarea CREATE_ENTRY. */
    record CreatePurchaseEntry(Long purchaseId, Long supplierId, String sku, String conditionCode, int quantity,
                               BigDecimal unitCost, String costCurrency, LocalDate expectedArrivalDate,
                               List<Long> reservations) {
    }

    /** {@code unitByOrderItem}: unidad de inventario reservada para cada unidad de pedido. */
    record PurchaseEntryResult(Long entryId, Map<Long, Long> unitByOrderItem) {
    }
}
