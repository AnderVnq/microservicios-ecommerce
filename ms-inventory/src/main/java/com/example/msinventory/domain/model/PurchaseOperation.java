package com.example.msinventory.domain.model;

import java.time.Instant;
import java.util.List;

/** Operacion idempotente sobre una compra (llegada o no recibido): guarda los unitIds para el replay. */
public record PurchaseOperation(Long id, String idempotencyKey, String requestHash, Long purchaseId,
                                PurchaseOperationType type, List<Long> unitIds, Instant createdAt) {
}
