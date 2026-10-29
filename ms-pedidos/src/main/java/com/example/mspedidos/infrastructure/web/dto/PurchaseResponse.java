package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.application.port.out.InventorySyncState;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Compra con detalle; {@code statusChangeRequest} solo aparece al marcar no recibidas con solicitud. */
public record PurchaseResponse(
        Long id, Long supplierId, String sku, String conditionCode, int quantity, BigDecimal unitCost,
        String costCurrency, String supplierPurchaseId, String invoiceNumber, LocalDate expectedArrivalDate,
        int receivedQuantity, int notReceivedQuantity, int pendingQuantity, Long inventoryEntryId,
        String createdBy, Instant createdAt,
        List<CoveredItem> coveredItems, int pendingSurplus, List<Arrival> arrivals, List<NonReceipt> nonReceipts,
        InventorySyncState inventorySync, List<String> warnings,
        @JsonInclude(JsonInclude.Include.NON_NULL) RequestRef statusChangeRequest) {

    public record CoveredItem(Long itemId, Long orderId, OrderItemStatus status) {
    }

    public record Arrival(Long id, int quantity, List<Long> orderItemIds, int surplusQuantity, String createdBy,
                          Instant createdAt) {
    }

    public record NonReceipt(Long id, int quantity, List<Long> orderItemIds, int surplusQuantity, String reason,
                             Long statusChangeRequestId, String appliedBy, Instant appliedAt) {
    }

    public record RequestRef(Long id, StatusChangeRequestStatus status) {
    }

    public static PurchaseResponse from(PurchaseView v) {
        return from(v, null);
    }

    public static PurchaseResponse from(PurchaseView v, StatusChangeRequest request) {
        Purchase p = v.purchase();
        return new PurchaseResponse(p.getId(), p.getSupplierId(), p.getSku(), p.getConditionCode(), p.getQuantity(),
                p.getUnitCost(), p.getCostCurrency(), p.getSupplierPurchaseId(), p.getInvoiceNumber(),
                p.getExpectedArrivalDate(), p.getReceivedQuantity(), p.getNotReceivedQuantity(), p.pending(),
                p.getInventoryEntryId(), p.getCreatedBy(), p.getCreatedAt(),
                v.coveredItems().stream().map(i -> new CoveredItem(i.getId(), i.getOrderId(), i.getStatus())).toList(),
                v.pendingSurplus(),
                v.arrivals().stream().map(a -> new Arrival(a.getId(), a.getQuantity(), a.getOrderItemIds(),
                        a.getSurplusQuantity(), a.getCreatedBy(), a.getCreatedAt())).toList(),
                v.nonReceipts().stream().map(n -> new NonReceipt(n.getId(), n.getQuantity(), n.getOrderItemIds(),
                        n.getSurplusQuantity(), n.getReason(), n.getStatusChangeRequestId(), n.getAppliedBy(),
                        n.getAppliedAt())).toList(),
                v.inventorySync(), v.warnings(),
                request == null ? null : new RequestRef(request.getId(), request.getStatus()));
    }
}
