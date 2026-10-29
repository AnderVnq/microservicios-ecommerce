package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.OrderLine;
import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.QuotaLockPort;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Expande lineas en unidades de una orden: las primeras tantas como cupo le quede al documento del titular
 * quedan {@code HOLDER}, el resto {@code UNASSIGNED} con incidencia {@code IMPORTER_ASSIGNMENT_PENDING}.
 * Debe ejecutarse dentro de una transaccion (toma el lock de cupo del titular, despues del de la orden).
 */
@Component
@RequiredArgsConstructor
class UnitCreator {

    private final QuotaLockPort quotaLock;
    private final QuotaAllocator quotaAllocator;
    private final OrderItemRepositoryPort itemRepository;
    private final ItemIncidentRepositoryPort incidentRepository;
    private final ItemHistoryRecorder history;

    List<OrderItem> createUnits(Order order, ImporterDocument holderDocument, List<OrderLine> lines, String actor) {
        int year = order.quotaYear();
        quotaLock.lock(holderDocument, year);
        long holderSlots = quotaAllocator.available(holderDocument, year, null);

        List<OrderItem> units = new ArrayList<>();
        for (OrderLine line : lines) {
            for (int i = 0; i < line.quantity(); i++) {
                OrderItem unit = OrderItem.newUnit(line.sku(), line.externalItemId(), line.unitPrice());
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
