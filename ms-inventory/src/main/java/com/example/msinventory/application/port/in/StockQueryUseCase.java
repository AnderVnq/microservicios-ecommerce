package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.Warehouse;

import java.time.LocalDate;
import java.util.List;

public interface StockQueryUseCase {

    /** Saldo actual (en camino + disponible) por sku/condicion/almacen. Filtros opcionales. */
    List<StockRow> stock(String sku, Warehouse warehouse, String conditionCode);

    /**
     * Kardex de un sku. {@code currency} null = USD; {@code from}/{@code to} inclusivos en UTC
     * (from null = desde el inicio, to null = hasta ahora). El saldo inicial es el de los movimientos previos a {@code from}.
     */
    KardexReport kardex(String sku, Warehouse warehouse, String conditionCode, String currency,
                        LocalDate from, LocalDate to);
}
