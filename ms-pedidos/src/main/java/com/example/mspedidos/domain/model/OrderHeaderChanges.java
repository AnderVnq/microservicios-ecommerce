package com.example.mspedidos.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Campos editables de la cabecera de una orden. */
public record OrderHeaderChanges(String orderNumber, LocalDateTime shippingDeadline,
                                 LocalDateTime usaDepartureDeadline, LocalDateTime deliveryDate,
                                 Integer leadTimeToShip, String shippingMethod, BigDecimal shippingCost,
                                 BigDecimal orderExpenses, BigDecimal storeDiscountTotal, String note) {
}
