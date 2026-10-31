package com.example.mspedidos.application.port.out;

import java.util.Collection;
import java.util.Map;

/** Catalogo de productos (ms-products): equivalencia entre SKU del market y SKU interno. */
public interface ProductsPort {

    /**
     * Resuelve SKUs de un market a SKU interno. La clave es el marketSku tal como se envio; solo se incluyen los
     * resueltos.
     *
     * @throws com.example.mspedidos.domain.exception.ProductsUnavailableException si ms-products no responde
     */
    Map<String, String> resolve(Long marketId, Collection<String> marketSkus);

    /**
     * Valida SKUs internos tecleados. La clave es el SKU tal como se envio y el valor la escritura guardada en el
     * catalogo (sin distinguir mayusculas ni espacios al inicio/fin); los que no existen no aparecen. Quien guarda el
     * SKU debe guardar el valor, no la entrada.
     *
     * @throws com.example.mspedidos.domain.exception.ProductsUnavailableException si ms-products no responde
     */
    Map<String, String> canonical(Collection<String> skus);
}
