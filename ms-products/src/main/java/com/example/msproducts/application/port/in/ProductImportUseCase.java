package com.example.msproducts.application.port.in;

import java.util.List;

/**
 * Carga masiva del catalogo: filas planas (una por SKU) agrupadas por mainSku.
 * Mas de 5000 filas -> IllegalArgumentException (422) sin procesar nada; ms-markets
 * caido -> MarketsUnavailableException (503) sin escribir nada. Cualquier otro
 * problema se informa por fila en el resultado.
 */
public interface ProductImportUseCase {

    int MAX_ROWS = 5000;

    ImportResult importRows(List<ImportRow> rows);
}
