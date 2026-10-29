package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.Purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Compra sin detalle (listados). */
public record PurchaseSummaryResponse(
        Long id, Long supplierId, String sku, String conditionCode, int quantity, BigDecimal unitCost,
        String costCurrency, String supplierPurchaseId, String invoiceNumber, LocalDate expectedArrivalDate,
        int receivedQuantity, int notReceivedQuantity, int pendingQuantity, Long inventoryEntryId,
        String createdBy, Instant createdAt) {

    public static PurchaseSummaryResponse from(Purchase p) {
        return new PurchaseSummaryResponse(p.getId(), p.getSupplierId(), p.getSku(), p.getConditionCode(),
                p.getQuantity(), p.getUnitCost(), p.getCostCurrency(), p.getSupplierPurchaseId(),
                p.getInvoiceNumber(), p.getExpectedArrivalDate(), p.getReceivedQuantity(),
                p.getNotReceivedQuantity(), p.pending(), p.getInventoryEntryId(), p.getCreatedBy(), p.getCreatedAt());
    }
}
