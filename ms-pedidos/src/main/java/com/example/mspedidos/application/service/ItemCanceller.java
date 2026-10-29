package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cancela una unidad, resuelve su incidencia de importador pendiente y escribe el historial; requiere transaccion
 * y que el llamador ya tenga el lock de la orden y de la unidad. Si la unidad sigue como la dejo una asignacion de
 * inventario, el dominio la marca {@code inventoryReleasePending}; el llamador libera en ms-inventory despues del
 * commit con {@link InventoryReleaser}.
 */
@Component
@RequiredArgsConstructor
class ItemCanceller {

    private final OrderItemRepositoryPort itemRepository;
    private final ItemIncidentRepositoryPort incidentRepository;
    private final ItemHistoryRecorder history;

    OrderItem cancel(OrderItem item, String actor) {
        String from = item.getStatus().name();
        item.cancel();
        OrderItem saved = itemRepository.save(item);
        history.record(saved.getId(), HistoryEventType.CANCELLED, from, saved.getStatus().name(), actor);
        incidentRepository.findOpen(saved.getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING).ifPresent(incident -> {
            incident.resolve(actor);
            incidentRepository.save(incident);
            history.record(saved.getId(), HistoryEventType.INCIDENT_RESOLVED,
                    IncidentType.IMPORTER_ASSIGNMENT_PENDING.name(), null, actor);
        });
        return saved;
    }
}
