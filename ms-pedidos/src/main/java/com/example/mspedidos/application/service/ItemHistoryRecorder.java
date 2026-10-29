package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.OrderItemHistoryRepositoryPort;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Escribe el historial de una unidad; debe llamarse dentro de la transaccion que cambia la unidad. */
@Component
@RequiredArgsConstructor
class ItemHistoryRecorder {

    private final OrderItemHistoryRepositoryPort historyRepository;

    void record(Long itemId, HistoryEventType type, String from, String to, String actor) {
        historyRepository.append(new OrderItemHistoryEntry(null, itemId, type, from, to, actor, Instant.now()));
    }

    /** {@code UNASSIGNED} o {@code <IMPORTER_TYPE>:<numeroDocumento>}, p. ej. {@code HOLDER:12345678}. */
    static String importerLabel(OrderItem item) {
        if (item.getImporterType() == ImporterType.UNASSIGNED || item.getImporterDocument() == null) {
            return ImporterType.UNASSIGNED.name();
        }
        return item.getImporterType().name() + ":" + item.getImporterDocument().number();
    }
}
