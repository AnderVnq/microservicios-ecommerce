package com.example.mspedidos.application.port.in;

/** Enlace de unidades pendientes (sin SKU interno) con el catalogo de productos (spec 8.3). */
public interface ProductLinkUseCase {

    /** Enlaza las unidades pendientes cuyo SKU de market ya tiene equivalencia; devuelve cuantas enlazo. */
    int linkPending();
}
