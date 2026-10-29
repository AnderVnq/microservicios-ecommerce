package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.IncidentUseCase;
import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.domain.exception.IncidentNotFoundException;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.exception.SystemManagedIncidentException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class IncidentService implements IncidentUseCase {

    private final ItemIncidentRepositoryPort incidentRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final ActorProvider actorProvider;
    private final ItemHistoryRecorder history;
    private final TransactionTemplate tx;

    IncidentService(ItemIncidentRepositoryPort incidentRepository, OrderItemRepositoryPort itemRepository,
                    ActorProvider actorProvider, ItemHistoryRecorder history,
                    PlatformTransactionManager transactionManager) {
        this.incidentRepository = incidentRepository;
        this.itemRepository = itemRepository;
        this.actorProvider = actorProvider;
        this.history = history;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<ItemIncident> search(IncidentStatus status, IncidentType type, IncidentScope scope) {
        return incidentRepository.search(status, type, scope);
    }

    @Override
    public ItemIncident open(Long orderId, Long itemId, IncidentType type, IncidentScope scope,
                             String description) {
        if (type.isSystemManaged()) {
            throw new SystemManagedIncidentException("La incidencia " + type + " la gestiona el sistema");
        }
        String actor = actorProvider.currentActor();
        return tx.execute(status -> {
            itemRepository.findByIdAndOrderId(itemId, orderId)
                    .orElseThrow(() -> new OrderItemNotFoundException(itemId));
            ItemIncident saved = incidentRepository.save(ItemIncident.open(itemId, type, scope, description, actor));
            history.record(itemId, HistoryEventType.INCIDENT_OPENED, null, type.name(), actor);
            return saved;
        });
    }

    @Override
    public ItemIncident resolve(Long incidentId) {
        String actor = actorProvider.currentActor();
        return tx.execute(status -> {
            ItemIncident incident = incidentRepository.findById(incidentId)
                    .orElseThrow(() -> new IncidentNotFoundException(incidentId));
            if (incident.getType().isSystemManaged()) {
                throw new SystemManagedIncidentException(
                        "La incidencia " + incident.getType() + " la gestiona el sistema");
            }
            incident.resolve(actor);
            ItemIncident saved = incidentRepository.save(incident);
            history.record(incident.getOrderItemId(), HistoryEventType.INCIDENT_RESOLVED,
                    incident.getType().name(), null, actor);
            return saved;
        });
    }
}
