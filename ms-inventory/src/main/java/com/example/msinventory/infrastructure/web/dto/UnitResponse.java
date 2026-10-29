package com.example.msinventory.infrastructure.web.dto;

import com.example.msinventory.domain.model.EntryType;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record UnitResponse(Long id, String sku, String conditionCode, Warehouse warehouse,
                           InventoryUnitStatus status, BigDecimal unitCost, String costCurrency,
                           EntryType entryType, String invoiceNumber, String supplierPurchaseId,
                           String sourceReference, LocalDate expectedArrivalDate, Instant arrivedAt,
                           LocalDate supplierReturnDeadline, Long allocatedOrderItemId, Long purchaseId, Instant createdAt,
                           Instant updatedAt) {

    public static UnitResponse from(InventoryUnit u) {
        return new UnitResponse(u.getId(), u.getSku(), u.getConditionCode(), u.getWarehouse(), u.getStatus(),
                u.getUnitCost(), u.getCostCurrency(), u.getEntryType(), u.getInvoiceNumber(),
                u.getSupplierPurchaseId(), u.getSourceReference(), u.getExpectedArrivalDate(), u.getArrivedAt(),
                u.getSupplierReturnDeadline(), u.getAllocatedOrderItemId(), u.getPurchaseId(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
