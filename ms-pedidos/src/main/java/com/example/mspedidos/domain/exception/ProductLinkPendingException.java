package com.example.mspedidos.domain.exception;

/** La unidad aun no tiene SKU interno (sin equivalencia en el catalogo). */
public class ProductLinkPendingException extends BusinessRuleException {

    public ProductLinkPendingException(String message) {
        super(message);
    }
}
