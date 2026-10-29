package com.example.mspedidos.domain.exception;

/** El SKU de la unidad cambio mientras ms-inventory asignaba una unidad del SKU anterior. */
public class ItemSkuChangedException extends BusinessRuleException {

    public ItemSkuChangedException(String message) {
        super(message);
    }
}
