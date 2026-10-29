package com.example.mspedidos.application.port.in;

import java.math.BigDecimal;

/** Linea de pedido: se expande en {@code quantity} unidades. */
public record OrderLine(String sku, String externalItemId, int quantity, BigDecimal unitPrice) {
}
