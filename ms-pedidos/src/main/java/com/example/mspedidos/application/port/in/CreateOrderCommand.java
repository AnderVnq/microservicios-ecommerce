package com.example.mspedidos.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateOrderCommand(String orderNumber, String externalOrderId, Long marketId, Long holderId,
                                 LocalDateTime orderDate, LocalDateTime shippingDeadline,
                                 LocalDateTime usaDepartureDeadline, LocalDateTime deliveryDate,
                                 Integer leadTimeToShip, String shippingMethod, BigDecimal shippingCost,
                                 BigDecimal orderExpenses, BigDecimal storeDiscountTotal, String note,
                                 List<OrderLine> lines) {
}
