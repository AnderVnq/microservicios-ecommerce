package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.KardexLine;
import com.example.msinventory.application.port.in.KardexReport;
import com.example.msinventory.application.port.in.StockQueryUseCase;
import com.example.msinventory.application.port.in.StockRow;
import com.example.msinventory.application.port.out.InventoryMovementRepositoryPort;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort.UnitFilter;
import com.example.msinventory.domain.model.Direction;
import com.example.msinventory.domain.model.InventoryMovement;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.MovementType;
import com.example.msinventory.domain.model.Warehouse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class StockQueryService implements StockQueryUseCase {

    private static final String DEFAULT_CURRENCY = "USD";

    private final InventoryUnitRepositoryPort units;
    private final InventoryMovementRepositoryPort movements;

    public StockQueryService(InventoryUnitRepositoryPort units, InventoryMovementRepositoryPort movements) {
        this.units = units;
        this.movements = movements;
    }

    private record Key(String sku, String conditionCode, Warehouse warehouse) {
    }

    @Override
    public List<StockRow> stock(String sku, Warehouse warehouse, String conditionCode) {
        Map<Key, long[]> counts = new TreeMap<>(Comparator.comparing(Key::sku)
                .thenComparing(Key::conditionCode).thenComparing(Key::warehouse));
        for (InventoryUnit u : units.search(new UnitFilter(sku, null, warehouse, conditionCode, null))) {
            InventoryUnitStatus s = u.getStatus();
            if (s != InventoryUnitStatus.IN_TRANSIT && s != InventoryUnitStatus.AVAILABLE) {
                continue;
            }
            long[] c = counts.computeIfAbsent(new Key(u.getSku(), u.getConditionCode(), u.getWarehouse()),
                    k -> new long[2]);
            c[s == InventoryUnitStatus.IN_TRANSIT ? 0 : 1]++;
        }
        return counts.entrySet().stream()
                .map(e -> new StockRow(e.getKey().sku(), e.getKey().conditionCode(), e.getKey().warehouse(),
                        e.getValue()[0], e.getValue()[1], e.getValue()[0] + e.getValue()[1]))
                .toList();
    }

    @Override
    public KardexReport kardex(String sku, Warehouse warehouse, String conditionCode, String currency,
                               LocalDate from, LocalDate to) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("El sku es obligatorio");
        }
        String cur = currency == null ? DEFAULT_CURRENCY : currency;
        Instant lower = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        // limite superior inclusivo (findForKardex): ultimo microsegundo del dia (datetime(6) de MySQL redondea nanos)
        Instant upper = to == null ? Instant.now()
                : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minus(1, ChronoUnit.MICROS);

        long balanceQty = 0;
        BigDecimal balanceValue = BigDecimal.ZERO.setScale(2);
        long openingQty = 0;
        BigDecimal openingValue = balanceValue;
        boolean openingClosed = lower == null;
        List<KardexLine> lines = new ArrayList<>();

        for (InventoryMovement m : movements.findForKardex(sku, conditionCode, cur, upper)) {
            if (!openingClosed && !m.occurredAt().isBefore(lower)) {
                openingQty = balanceQty;
                openingValue = balanceValue;
                openingClosed = true;
            }
            if (!belongs(m, warehouse)) {
                continue;
            }
            int signed = signedQuantity(m, warehouse);
            balanceQty += signed;
            balanceValue = balanceValue.add(m.unitCost().multiply(BigDecimal.valueOf(signed))).setScale(2,
                    RoundingMode.HALF_UP);
            if (openingClosed) {
                lines.add(new KardexLine(m.occurredAt(), m.type(), m.unitId(), m.reference(),
                        Math.max(signed, 0), Math.max(-signed, 0), m.unitCost(), balanceQty, balanceValue));
            }
        }
        if (!openingClosed) {
            openingQty = balanceQty;
            openingValue = balanceValue;
        }
        return new KardexReport(sku, conditionCode, warehouse, cur, openingQty, openingValue, lines,
                balanceQty, balanceValue);
    }

    private static boolean belongs(InventoryMovement m, Warehouse w) {
        if (w == null) {
            return true;
        }
        if (m.type() == MovementType.TRANSFER) {
            return m.fromWarehouse() == w || m.toWarehouse() == w;
        }
        return m.type().direction() == Direction.OUT ? m.fromWarehouse() == w : m.toWarehouse() == w;
    }

    private static int signedQuantity(InventoryMovement m, Warehouse w) {
        return switch (m.type().direction()) {
            case IN -> 1;
            case OUT -> -1;
            case INTERNAL -> {
                if (m.type() != MovementType.TRANSFER || w == null) {
                    yield 0;
                }
                yield m.fromWarehouse() == w ? -1 : 1;
            }
        };
    }
}
