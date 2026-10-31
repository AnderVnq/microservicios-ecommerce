package com.example.msinventory.application.port.out;

import java.util.Optional;

/** Consulta al catalogo de productos (ms-products). */
public interface ProductsPort {

    /**
     * SKU con la escritura guardada en el catalogo (sin distinguir mayusculas ni espacios al inicio/fin), o vacio si
     * no existe. Quien guarda el SKU debe guardar este valor, no el tecleado.
     *
     * @throws com.example.msinventory.domain.exception.ProductsUnavailableException si ms-products no responde.
     */
    Optional<String> canonicalSku(String sku);
}
