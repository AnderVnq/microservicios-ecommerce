package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

public record AllocateInventoryRequest(@NotNull Long inventoryUnitId) {
}
