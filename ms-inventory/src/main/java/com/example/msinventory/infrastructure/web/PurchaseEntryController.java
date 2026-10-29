package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.PurchaseEntryUseCase;
import com.example.msinventory.application.port.in.PurchaseEntryUseCase.PurchaseEntryResult;
import com.example.msinventory.infrastructure.web.dto.PurchaseEntryRequest;
import com.example.msinventory.infrastructure.web.dto.PurchaseEntryResponse;
import com.example.msinventory.infrastructure.web.dto.PurchaseUnitsRequest;
import com.example.msinventory.infrastructure.web.dto.PurchaseUnitsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador driver (interno de ms-pedidos): compras a proveedor, idempotentes por Idempotency-Key. */
@RestController
@RequestMapping("/api/v1/inventory/purchase-entries")
@RequiredArgsConstructor
public class PurchaseEntryController {

    private final PurchaseEntryUseCase purchases;
    private final RequestHasher hasher;

    @PostMapping
    public ResponseEntity<PurchaseEntryResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        @Valid @RequestBody PurchaseEntryRequest request) {
        requireKey(idempotencyKey);
        PurchaseEntryResult result = purchases.create(idempotencyKey, hasher.hash(request), request.toCommand());
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(PurchaseEntryResponse.from(result));
    }

    @PostMapping("/{purchaseId}/arrivals")
    public PurchaseUnitsResponse arrivals(@PathVariable Long purchaseId,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @Valid @RequestBody PurchaseUnitsRequest request) {
        requireKey(idempotencyKey);
        return new PurchaseUnitsResponse(purchases.registerArrivals(idempotencyKey, hasher.hash(request),
                purchaseId, request.quantity(), request.orderItemIds()));
    }

    @PostMapping("/{purchaseId}/not-received")
    public PurchaseUnitsResponse notReceived(@PathVariable Long purchaseId,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @Valid @RequestBody PurchaseUnitsRequest request) {
        requireKey(idempotencyKey);
        return new PurchaseUnitsResponse(purchases.markNotReceived(idempotencyKey, hasher.hash(request),
                purchaseId, request.quantity(), request.orderItemIds(), request.reason()));
    }

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La cabecera Idempotency-Key es obligatoria");
        }
    }
}
