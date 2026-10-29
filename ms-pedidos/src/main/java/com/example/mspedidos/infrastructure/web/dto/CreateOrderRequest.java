package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.application.port.in.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateOrderRequest(
        String orderNumber,
        @NotBlank String externalOrderId,
        @NotNull Long marketId,
        @NotNull Long holderId,
        @NotNull LocalDateTime orderDate,
        LocalDateTime shippingDeadline,
        LocalDateTime usaDepartureDeadline,
        LocalDateTime deliveryDate,
        Integer leadTimeToShip,
        String shippingMethod,
        @DecimalMin("0") BigDecimal shippingCost,
        @DecimalMin("0") BigDecimal orderExpenses,
        @DecimalMin("0") BigDecimal storeDiscountTotal,
        String note,
        @NotEmpty List<@NotNull @Valid OrderLineRequest> lines
) {
    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(orderNumber, externalOrderId, marketId, holderId, orderDate,
                shippingDeadline, usaDepartureDeadline, deliveryDate, leadTimeToShip, shippingMethod,
                shippingCost, orderExpenses, storeDiscountTotal, note,
                lines.stream().map(OrderLineRequest::toLine).toList());
    }
}
