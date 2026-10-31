package com.example.msproducts.application.port.out;

/**
 * Puerto de salida hacia ms-markets: permite validar que un market existe
 * antes de registrar equivalencias de SKU.
 */
public interface MarketsPort {

    /** Indica si existe el market con ese id en ms-markets. */
    boolean exists(Long marketId);
}
