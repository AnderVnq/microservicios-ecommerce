package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.OrderLine;
import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.ProductsPort;
import com.example.mspedidos.application.port.out.QuotaLockPort;
import com.example.mspedidos.domain.exception.ProductsUnavailableException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Expande lineas en unidades de una orden: las primeras tantas como cupo le quede al documento del titular
 * quedan {@code HOLDER}, el resto {@code UNASSIGNED} con incidencia {@code IMPORTER_ASSIGNMENT_PENDING}.
 * Debe ejecutarse dentro de una transaccion (toma el lock de cupo del titular, despues del de la orden).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UnitCreator {

    private final QuotaLockPort quotaLock;
    private final QuotaAllocator quotaAllocator;
    private final OrderItemRepositoryPort itemRepository;
    private final ItemIncidentRepositoryPort incidentRepository;
    private final ItemHistoryRecorder history;
    private final ProductsPort productsPort;

    /**
     * Resuelve los SKUs de market de las lineas a SKU interno. Es una llamada remota: debe hacerse ANTES de abrir la
     * transaccion. Si ms-products no responde o falla de cualquier otra forma (spec 8.2: "caido / error"), devuelve
     * vacio: todas las unidades quedan pendientes de vinculo y el job las enlaza despues.
     */
    Map<String, String> resolveSkus(Long marketId, List<OrderLine> lines) {
        LinkedHashSet<String> marketSkus = new LinkedHashSet<>();
        lines.forEach(l -> marketSkus.add(l.marketSku()));
        try {
            return productsPort.resolve(marketId, marketSkus);
        } catch (ProductsUnavailableException e) {
            log.warn("ms-products no disponible; las unidades quedan pendientes de vinculo: {}", e.getMessage());
            return Map.of();
        } catch (RuntimeException e) {
            log.warn("Error al resolver SKUs en ms-products; las unidades quedan pendientes de vinculo", e);
            return Map.of();
        }
    }

    List<OrderItem> createUnits(Order order, ImporterDocument holderDocument, List<OrderLine> lines,
                            Map<String, String> resolved, String actor) {
        int year = order.quotaYear();
        quotaLock.lock(holderDocument, year);
        long holderSlots = quotaAllocator.available(holderDocument, year, null);

        List<OrderItem> units = new ArrayList<>();
        for (OrderLine line : lines) {
            for (int i = 0; i < line.quantity(); i++) {
                OrderItem unit = OrderItem.newUnit(line.marketSku(), resolved.get(line.marketSku()), line.externalItemId(), line.unitPrice());
                unit.attachTo(order.getId());
                if (units.size() < holderSlots) {
                    unit.assignHolder(holderDocument);
                }
                units.add(unit);
            }
        }

        List<OrderItem> saved = itemRepository.saveAll(units);
        for (OrderItem unit : saved) {
            history.record(unit.getId(), HistoryEventType.CREATED, null, OrderItemStatus.PROCESSING.name(), actor);
            if (unit.isProductLinkPending()) {
                incidentRepository.save(ItemIncident.productLinkPending(unit.getId(), unit.getMarketSku(), actor));
                history.record(unit.getId(), HistoryEventType.INCIDENT_OPENED, null,
                        IncidentType.PRODUCT_LINK_PENDING.name(), actor);
            }
            if (unit.getImporterType() == ImporterType.HOLDER) {
                history.record(unit.getId(), HistoryEventType.IMPORTER_ASSIGNED, ImporterType.UNASSIGNED.name(),
                        ItemHistoryRecorder.importerLabel(unit), actor);
            } else {
                incidentRepository.save(ItemIncident.importerPending(unit.getId(), actor));
                history.record(unit.getId(), HistoryEventType.INCIDENT_OPENED, null,
                        IncidentType.IMPORTER_ASSIGNMENT_PENDING.name(), actor);
            }
        }
        return saved;
    }
}
