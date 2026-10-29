package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.PurchaseQuantityExceededException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Compra a un proveedor; siempre llega al almacen USA. */
@Getter
@Builder
@AllArgsConstructor
public class Purchase {

    private Long id;
    private String idempotencyKey;
    private String requestHash;
    private Long supplierId;
    private String sku;
    private String conditionCode;
    private int quantity;
    private BigDecimal unitCost;
    private String costCurrency;
    private String supplierPurchaseId;
    private String invoiceNumber;
    private LocalDate expectedArrivalDate;
    private int receivedQuantity;
    private int notReceivedQuantity;
    private Long inventoryEntryId;
    private String createdBy;
    private Instant createdAt;

    /** Unidades que aun no llegaron ni se marcaron como no recibidas. */
    public int pending() {
        return quantity - receivedQuantity - notReceivedQuantity;
    }

    /** setAside = unidades del lote que quedan como sobrante (no cubren pedidos). */
    public void registerArrival(int qty, int setAside) {
        ensureWithinPending(qty, setAside);
        this.receivedQuantity += qty;
    }

    public void registerNotReceived(int qty, int setAside) {
        ensureWithinPending(qty, setAside);
        this.notReceivedQuantity += qty;
    }

    public void linkInventoryEntry(Long entryId) {
        this.inventoryEntryId = entryId;
    }

    private void ensureWithinPending(int qty, int setAside) {
        if (qty < 1 || qty > pending() - setAside) {
            throw new PurchaseQuantityExceededException(
                    "Cantidad invalida: " + qty + " (pendiente " + pending() + ", reservado " + setAside + ")");
        }
    }
}
