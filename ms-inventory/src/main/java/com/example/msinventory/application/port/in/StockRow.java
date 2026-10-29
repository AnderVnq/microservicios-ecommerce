package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.Warehouse;

/** {@code total = inTransit + available}; ALLOCATED y salidas no cuentan. */
public record StockRow(String sku, String conditionCode, Warehouse warehouse, long inTransit, long available,
                       long total) {
}
