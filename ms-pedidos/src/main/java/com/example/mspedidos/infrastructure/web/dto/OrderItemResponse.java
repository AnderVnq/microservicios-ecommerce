package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.DeliveredTo;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderItemResponse(
        Long id,
        Long orderId,
        String sku,
        String externalItemId,
        BigDecimal unitPrice,
        OrderItemStatus status,
        Warehouse warehouse,
        DeliveredTo deliveredTo,
        ImporterType importerType,
        Long inventoryUnitId,
        boolean inventoryReleasePending,
        Long familyMemberId,
        String importerDocumentType,
        String importerDocumentNumber,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderItemResponse from(OrderItem i) {
        var doc = i.getImporterDocument();
        return new OrderItemResponse(i.getId(), i.getOrderId(), i.getSku(), i.getExternalItemId(),
                i.getUnitPrice(), i.getStatus(), i.getWarehouse(), i.getDeliveredTo(), i.getImporterType(),
                i.getInventoryUnitId(), i.isInventoryReleasePending(), i.getFamilyMemberId(), doc == null ? null : doc.type(), doc == null ? null : doc.number(),
                i.getCreatedAt(), i.getUpdatedAt());
    }
}
