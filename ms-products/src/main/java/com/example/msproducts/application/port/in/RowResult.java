package com.example.msproducts.application.port.in;

import java.util.List;

/** Resultado de una fila; {@code row} es la posicion (desde 1) en la peticion. */
public record RowResult(int row, String sku, RowOutcome result, List<String> reasons,
                        List<IgnoredDifference> ignoredDifferences) {
}
