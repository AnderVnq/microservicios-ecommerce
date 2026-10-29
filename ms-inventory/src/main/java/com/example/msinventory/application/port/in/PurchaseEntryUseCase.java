package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.InventoryEntry;
import com.example.msinventory.domain.model.InventoryUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Compras a proveedor: entrada en transito (USA) con reservas, llegadas y no recibidos parciales. */
public interface PurchaseEntryUseCase {

    PurchaseEntryResult create(String idempotencyKey, String requestHash, CreatePurchaseEntryCommand cmd);

    /** @return ids de las unidades que llegaron (los de la primera ejecucion si es un replay). */
    List<Long> registerArrivals(String idempotencyKey, String requestHash, Long purchaseId, int quantity,
                                List<Long> orderItemIds);

    /** @return ids de las unidades marcadas como no recibidas (los de la primera ejecucion si es un replay). */
    List<Long> markNotReceived(String idempotencyKey, String requestHash, Long purchaseId, int quantity,
                               List<Long> orderItemIds, String reason);

    record CreatePurchaseEntryCommand(Long purchaseId, Long supplierId, String sku, String conditionCode,
                                      int quantity, BigDecimal unitCost, String costCurrency,
                                      LocalDate expectedArrivalDate, List<Long> reservations) {
    }

    /**
     * {@code reservations}: ids de item de pedido de la solicitud original; {@code reservations.get(i)} corresponde
     * a {@code units.get(i)} (unidades por id = orden de creacion), sin depender del estado actual de las unidades.
     */
    record PurchaseEntryResult(InventoryEntry entry, List<InventoryUnit> units, List<Long> reservations,
                               boolean replayed) {
    }
}
