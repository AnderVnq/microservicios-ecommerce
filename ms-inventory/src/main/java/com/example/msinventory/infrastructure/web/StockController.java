package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.KardexReport;
import com.example.msinventory.application.port.in.StockQueryUseCase;
import com.example.msinventory.application.port.in.StockRow;
import com.example.msinventory.domain.model.Warehouse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Adaptador driver: saldo actual y kardex. */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class StockController {

    private final StockQueryUseCase stock;

    @GetMapping("/stock")
    public List<StockRow> stock(@RequestParam(required = false) String sku,
                                @RequestParam(required = false) Warehouse warehouse,
                                @RequestParam(required = false) String conditionCode) {
        return stock.stock(sku, warehouse, conditionCode);
    }

    @GetMapping("/kardex")
    public KardexReport kardex(@RequestParam String sku,
                               @RequestParam(required = false) Warehouse warehouse,
                               @RequestParam(required = false) String conditionCode,
                               @RequestParam(required = false) String currency,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stock.kardex(sku, warehouse, conditionCode, currency, from, to);
    }
}
