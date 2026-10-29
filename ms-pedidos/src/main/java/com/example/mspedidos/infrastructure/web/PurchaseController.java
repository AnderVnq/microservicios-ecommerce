package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.NotReceivedResult;
import com.example.mspedidos.application.port.in.PurchaseUseCase;
import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.application.port.in.RegisterPurchaseCommand;
import com.example.mspedidos.application.port.out.InventorySyncState;
import com.example.mspedidos.application.port.out.PurchaseFilter;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.infrastructure.web.dto.ArrivalRequest;
import com.example.mspedidos.infrastructure.web.dto.NotReceivedRequest;
import com.example.mspedidos.infrastructure.web.dto.PurchaseResponse;
import com.example.mspedidos.infrastructure.web.dto.PurchaseSummaryResponse;
import com.example.mspedidos.infrastructure.web.dto.RegisterPurchaseRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador driver: compras a proveedores. */
@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseUseCase purchaseUseCase;
    private final RequestHasher hasher;

    @PostMapping
    public ResponseEntity<PurchaseResponse> register(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     @Valid @RequestBody RegisterPurchaseRequest r) {
        PurchaseView view = purchaseUseCase.register(idempotencyKey, hasher.hash(r), new RegisterPurchaseCommand(
                r.supplierId(), r.sku(), r.conditionCode(), r.quantity(), r.unitCost(), r.costCurrency(),
                r.supplierPurchaseId(), r.invoiceNumber(), r.expectedArrivalDate(), r.orderItemIds()));
        return created(view);
    }

    @GetMapping
    public List<PurchaseSummaryResponse> search(@RequestParam(required = false) Long supplierId,
                                                @RequestParam(required = false) String sku,
                                                @RequestParam(required = false) String invoiceNumber,
                                                @RequestParam(required = false) String supplierPurchaseId,
                                                @RequestParam(required = false) Boolean pending,
                                                @RequestParam(required = false) InventorySyncState inventorySync) {
        return purchaseUseCase.search(new PurchaseFilter(supplierId, sku, invoiceNumber, supplierPurchaseId,
                pending, inventorySync)).stream().map(PurchaseSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PurchaseResponse findOne(@PathVariable Long id) {
        return PurchaseResponse.from(purchaseUseCase.findOne(id));
    }

    @PostMapping("/{id}/arrivals")
    public ResponseEntity<PurchaseResponse> registerArrival(@PathVariable Long id,
                                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                            @Valid @RequestBody ArrivalRequest r) {
        return created(purchaseUseCase.registerArrival(id, idempotencyKey, hasher.hash(r), r.quantity()));
    }

    /**
     * 202 si quedo una solicitud {@code PENDING}; 200 si se aplico (directo, sobrante o {@code APPLIED_DIRECTLY}).
     * Una repeticion con la misma {@code Idempotency-Key} devuelve el estado actual del resultado original.
     */
    @PostMapping("/{id}/not-received")
    public ResponseEntity<PurchaseResponse> markNotReceived(@PathVariable Long id,
                                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                            @Valid @RequestBody NotReceivedRequest r) {
        NotReceivedResult result = purchaseUseCase.markNotReceived(id, idempotencyKey, hasher.hash(r), r.quantity(),
                r.reason());
        boolean pending = result.request() != null && result.request().getStatus() == StatusChangeRequestStatus.PENDING;
        return ResponseEntity.status(pending ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(PurchaseResponse.from(result.view(), result.request()));
    }

    @PostMapping("/{id}/inventory-sync/retry")
    public PurchaseResponse retrySync(@PathVariable Long id) {
        return PurchaseResponse.from(purchaseUseCase.retrySync(id));
    }

    /** 201 la primera vez, 200 si es repeticion de la misma {@code Idempotency-Key}. */
    private static ResponseEntity<PurchaseResponse> created(PurchaseView view) {
        return ResponseEntity.status(view.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(PurchaseResponse.from(view));
    }
}
