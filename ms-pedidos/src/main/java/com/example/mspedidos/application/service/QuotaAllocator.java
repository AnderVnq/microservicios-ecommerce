package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.domain.model.ImporterDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cupo de importacion por documento y anio calendario. Los resultados solo son fiables para decidir una
 * asignacion si se consultan con el lock del documento tomado ({@code QuotaLockPort}).
 */
@Component
public class QuotaAllocator {

    private final OrderItemRepositoryPort itemRepository;
    private final int maxUnitsPerYear;

    public QuotaAllocator(OrderItemRepositoryPort itemRepository,
                          @Value("${orders.importer-quota.max-units-per-year:3}") int maxUnitsPerYear) {
        this.itemRepository = itemRepository;
        this.maxUnitsPerYear = maxUnitsPerYear;
    }

    public int maxUnitsPerYear() {
        return maxUnitsPerYear;
    }

    public long used(ImporterDocument doc, int year) {
        return itemRepository.countQuotaUnits(doc, year, null);
    }

    /** Unidades que aun puede importar el documento en el anio; {@code excludeItemId} no cuenta como usada. */
    public long available(ImporterDocument doc, int year, Long excludeItemId) {
        return Math.max(0, maxUnitsPerYear - itemRepository.countQuotaUnits(doc, year, excludeItemId));
    }
}
