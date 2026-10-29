package com.example.msinventory.infrastructure.web.dto;

import com.example.msinventory.domain.model.Warehouse;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TransferRequest(@NotNull Warehouse toWarehouse, @NotNull LocalDate expectedArrivalDate) {
}
