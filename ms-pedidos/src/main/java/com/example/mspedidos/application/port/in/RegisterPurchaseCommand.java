package com.example.mspedidos.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos para registrar una compra. {@code costCurrency} null = {@code USD}; {@code orderItemIds} null o vacio =
 * seleccion automatica de las unidades {@code PROCESSING} mas urgentes del sku.
 */
public record RegisterPurchaseCommand(Long supplierId, String sku, String conditionCode, int quantity,
                                      BigDecimal unitCost, String costCurrency, String supplierPurchaseId,
                                      String invoiceNumber, LocalDate expectedArrivalDate, List<Long> orderItemIds) {
}
