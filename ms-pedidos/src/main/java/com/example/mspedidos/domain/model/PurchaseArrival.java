package com.example.mspedidos.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/** Registro de una llegada (parcial o total) de una compra. */
@Getter
@Builder
@AllArgsConstructor
public class PurchaseArrival {

    private Long id;
    private Long purchaseId;
    private String idempotencyKey;
    private String requestHash;
    private int quantity;
    private List<Long> orderItemIds;
    private int surplusQuantity;
    private String createdBy;
    private Instant createdAt;
}
