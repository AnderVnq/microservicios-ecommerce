package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.Warehouse;

import java.math.BigDecimal;
import java.util.List;

public record KardexReport(String sku, String conditionCode, Warehouse warehouse, String currency,
                           long openingQuantity, BigDecimal openingValue, List<KardexLine> lines,
                           long closingQuantity, BigDecimal closingValue) {
}
