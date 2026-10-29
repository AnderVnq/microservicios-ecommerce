package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.OrderHeaderChanges;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Solo los campos editables de la cabecera. */
public record UpdateOrderRequest(
        String orderNumber,
        LocalDateTime shippingDeadline,
        LocalDateTime usaDepartureDeadline,
        LocalDateTime deliveryDate,
        Integer leadTimeToShip,
        String shippingMethod,
        @DecimalMin("0") BigDecimal shippingCost,
        @DecimalMin("0") BigDecimal orderExpenses,
        @DecimalMin("0") BigDecimal storeDiscountTotal,
        String note
) {
    public OrderHeaderChanges toChanges() {
        return new OrderHeaderChanges(orderNumber, shippingDeadline, usaDepartureDeadline, deliveryDate,
                leadTimeToShip, shippingMethod, shippingCost, orderExpenses, storeDiscountTotal, note);
    }
}
