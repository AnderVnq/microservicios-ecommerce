package com.example.msinventory.infrastructure.web.dto;

import com.example.msinventory.application.port.in.RegisterEntryCommand;
import com.example.msinventory.domain.model.EntryType;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegisterEntryRequest(
        @NotNull EntryType entryType,
        @NotBlank String sku,
        @NotBlank String conditionCode,
        @NotNull Warehouse warehouse,
        @NotNull @Min(1) @Max(500) Integer quantity,
        @NotNull @DecimalMin("0") BigDecimal unitCost,
        @Pattern(regexp = "[A-Z]{3}") String costCurrency,
        @NotNull InventoryUnitStatus status,
        LocalDate expectedArrivalDate,
        LocalDate supplierReturnDeadline,
        String invoiceNumber,
        String supplierPurchaseId,
        String sourceReference,
        String reason) {

    public RegisterEntryCommand toCommand() {
        return new RegisterEntryCommand(entryType, sku, conditionCode, warehouse, quantity, unitCost, costCurrency,
                status, expectedArrivalDate, supplierReturnDeadline, invoiceNumber, supplierPurchaseId,
                sourceReference, reason);
    }
}
