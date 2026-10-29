package com.example.msinventory.infrastructure.web.dto;

import com.example.msinventory.application.port.in.PurchaseEntryUseCase.CreatePurchaseEntryCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseEntryRequest(
        @NotNull Long purchaseId,
        @NotNull Long supplierId,
        @NotBlank String sku,
        @NotBlank String conditionCode,
        @NotNull @Min(1) @Max(500) Integer quantity,
        @NotNull @DecimalMin("0") BigDecimal unitCost,
        @Pattern(regexp = "[A-Z]{3}") String costCurrency,
        @NotNull LocalDate expectedArrivalDate,
        List<@NotNull Long> reservations) {

    /** Normaliza null a lista vacia para que el hash de la solicitud no dependa de omitir el campo. */
    public PurchaseEntryRequest {
        reservations = reservations == null ? List.of() : reservations;
    }

    public CreatePurchaseEntryCommand toCommand() {
        return new CreatePurchaseEntryCommand(purchaseId, supplierId, sku, conditionCode, quantity, unitCost,
                costCurrency, expectedArrivalDate, reservations);
    }
}
