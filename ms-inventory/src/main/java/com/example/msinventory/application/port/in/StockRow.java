package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.Warehouse;

/**
 * {@code total = inTransit + available}; ALLOCATED y salidas no cuentan. {@code reservedInTransit} son unidades
 * en transito con reserva: siguen contando dentro de {@code inTransit} y {@code total}.
 */
public record StockRow(String sku, String conditionCode, Warehouse warehouse, long inTransit, long available,
                       long total,
                       long reservedInTransit) {
}
