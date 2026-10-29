package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.MovementType;

import java.math.BigDecimal;
import java.time.Instant;

public record KardexLine(Instant occurredAt, MovementType type, Long unitId, String reference, long quantityIn,
                         long quantityOut, BigDecimal unitCost, long balanceQuantity, BigDecimal balanceValue) {
}
