package com.example.msproducts.infrastructure.web.dto;

import java.util.List;
import java.util.Map;

/**
 * existing: las entradas (tal como llegaron) cuyo SKU existe; se mantiene por compatibilidad.
 * canonical: entrada tal como llego -> SKU con la escritura guardada en el catalogo. Quien
 * valida un SKU tecleado debe guardar el valor de canonical, no el de la entrada.
 */
public record SkuExistsResponse(List<String> existing, Map<String, String> canonical) {

    public static SkuExistsResponse of(List<String> requested, Map<String, String> canonical) {
        return new SkuExistsResponse(requested.stream().filter(canonical::containsKey).toList(), canonical);
    }
}
