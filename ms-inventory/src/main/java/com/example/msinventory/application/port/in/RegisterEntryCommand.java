package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.EntryType;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code status} solo IN_TRANSIT o AVAILABLE; {@code costCurrency} null = USD. */
public record RegisterEntryCommand(
        EntryType entryType,
        String sku,
        String conditionCode,
        Warehouse warehouse,
        int quantity,
        BigDecimal unitCost,
        String costCurrency,
        InventoryUnitStatus status,
        LocalDate expectedArrivalDate,
        LocalDate supplierReturnDeadline,
        String invoiceNumber,
        String supplierPurchaseId,
        String sourceReference,
        String reason) {
}
