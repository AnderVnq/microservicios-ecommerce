package com.example.mspedidos.application.port.in;

import com.example.mspedidos.application.port.out.PurchaseFilter;
import com.example.mspedidos.domain.model.Purchase;

import java.util.List;

public interface PurchaseUseCase {

    /**
     * Registra una compra y cubre unidades de pedido (spec 8.1). Misma clave y mismo hash → la compra existente con
     * {@code replayed = true}; misma clave con otro hash → {@code IdempotencyKeyReusedException}.
     */
    PurchaseView register(String idempotencyKey, String requestHash, RegisterPurchaseCommand cmd);

    /**
     * Registra una llegada (spec 8.2): cubre las unidades {@code PURCHASED} mas urgentes primero; lo que sobra es
     * sobrante recibido. Las claves son globales: misma clave y mismo hash en la misma compra → la llegada existente
     * con {@code replayed = true}; en otra compra o con otro hash → {@code IdempotencyKeyReusedException}.
     * {@code quantity} mayor que {@code pendiente - apartado} → {@code PurchaseQuantityExceededException}.
     */
    PurchaseView registerArrival(Long purchaseId, String idempotencyKey, String requestHash, int quantity);

    /**
     * Marca unidades como no recibidas (spec 8.3): primero el sobrante pendiente, luego las unidades {@code PURCHASED}
     * menos urgentes. Si solo toca sobrante se aplica directo; si toca unidades de pedido crea una solicitud
     * {@code PURCHASE_NOT_RECEIVED} (aplicada en el acto si el actor tiene {@code status-change/apply-direct}), que
     * se devuelve en {@link NotReceivedResult#request()} (null si fue solo sobrante). Exige {@code Idempotency-Key}
     * (claves globales): misma clave y mismo hash en la misma compra → el resultado original (la misma solicitud, en
     * su estado actual, o la misma marca directa) sin aplicar nada; en otra compra o con otro hash →
     * {@code IdempotencyKeyReusedException}.
     */
    NotReceivedResult markNotReceived(Long purchaseId, String idempotencyKey, String requestHash, int quantity,
                                      String reason);

    /** Reintento manual de las tareas de sincronizacion {@code FAILED}; devuelve la compra refrescada (404 si no existe). */
    PurchaseView retrySync(Long purchaseId);

    PurchaseView findOne(Long id);

    /** Mas recientes primero. */
    List<Purchase> search(PurchaseFilter filter);
}
