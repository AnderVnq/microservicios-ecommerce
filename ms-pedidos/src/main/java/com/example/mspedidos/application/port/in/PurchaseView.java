package com.example.mspedidos.application.port.in;

import com.example.mspedidos.application.port.out.InventorySyncState;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.PurchaseArrival;
import com.example.mspedidos.domain.model.PurchaseNonReceipt;

import java.util.List;

/**
 * Compra con sus unidades de pedido cubiertas (por id), el sobrante pendiente de llegar
 * ({@code pendiente - unidades PURCHASED}), llegadas, no recibidas, estado de sincronizacion con inventario y avisos.
 * {@code replayed} = respuesta repetida de una misma {@code Idempotency-Key}.
 */
public record PurchaseView(Purchase purchase, List<OrderItem> coveredItems, int pendingSurplus,
                           List<PurchaseArrival> arrivals, List<PurchaseNonReceipt> nonReceipts,
                           InventorySyncState inventorySync, List<String> warnings, boolean replayed) {
}
