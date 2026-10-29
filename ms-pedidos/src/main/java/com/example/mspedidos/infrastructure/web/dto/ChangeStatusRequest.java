package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.DeliveredTo;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Warehouse;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull OrderItemStatus status, Warehouse warehouse, DeliveredTo deliveredTo) {
}
