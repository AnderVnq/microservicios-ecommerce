package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.PurchaseArrival;
import com.example.mspedidos.domain.model.PurchaseNonReceipt;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepositoryPort {

    Purchase save(Purchase purchase);

    Optional<Purchase> findById(Long id);

    /** Como {@link #findById} pero con lock pesimista de escritura; requiere transaccion. */
    Optional<Purchase> findByIdForUpdate(Long id);

    Optional<Purchase> findByIdempotencyKey(String key);

    /** Si otra compra del mismo proveedor ya usa el numero de factura; {@code excludeId} puede ser null. */
    boolean existsOtherWithInvoice(Long supplierId, String invoiceNumber, Long excludeId);

    /** Mas recientes primero. */
    List<Purchase> search(PurchaseFilter filter);

    PurchaseArrival saveArrival(PurchaseArrival arrival);

    Optional<PurchaseArrival> findArrivalByKey(String idempotencyKey);

    /** Por id ascendente. */
    List<PurchaseArrival> findArrivals(Long purchaseId);

    PurchaseNonReceipt saveNonReceipt(PurchaseNonReceipt nonReceipt);

    /** Marca directa de no recibidas registrada con esa {@code Idempotency-Key}. */
    Optional<PurchaseNonReceipt> findNonReceiptByKey(String idempotencyKey);

    /** Por id ascendente. */
    List<PurchaseNonReceipt> findNonReceipts(Long purchaseId);
}
