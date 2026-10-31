package com.example.msproducts.application.port.in;

import java.util.List;
import java.util.Map;

/** Resolucion masiva de SKUs (regla 6.7: equivalencia del market primero, luego SKU interno). */
public interface SkuResolutionUseCase {

    /** Una entrada por cada entrada, en el mismo orden. Maximo 500, mas lanza IllegalArgumentException. */
    List<SkuResolution> resolve(List<MarketSkuRef> refs);

    /**
     * Para cada cadena de entrada cuyo SKU existe (cualquier estado, sin distinguir mayusculas ni
     * espacios al inicio/fin) devuelve entrada tal como llego -> SKU con la escritura guardada.
     * Las que no existen no aparecen. Respeta el orden de entrada. Maximo 500.
     */
    Map<String, String> canonical(List<String> skus);
}
