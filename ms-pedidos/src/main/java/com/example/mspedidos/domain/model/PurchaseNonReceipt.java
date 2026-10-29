package com.example.mspedidos.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/** Registro de unidades de una compra que no llegaron. */
@Getter
@Builder
@AllArgsConstructor
public class PurchaseNonReceipt {

    private Long id;
    private Long purchaseId;
    private int quantity;
    private List<Long> orderItemIds;
    private int surplusQuantity;
    private String reason;
    private Long statusChangeRequestId;
    private String appliedBy;
    /** {@code Idempotency-Key} de la marca directa (solo sobrante); null si vino de una solicitud. */
    private String idempotencyKey;
    private String requestHash;
    private Instant appliedAt;
}
