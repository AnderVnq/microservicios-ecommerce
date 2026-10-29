package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.AllocationUndoUseCase;
import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.AllocationUndoRequestRepositoryPort;
import com.example.mspedidos.application.port.out.AuthorizationPort;
import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.OrderRepositoryPort;
import com.example.mspedidos.domain.exception.ApprovalForbiddenException;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.exception.OrderNotFoundException;
import com.example.mspedidos.domain.exception.UndoNotAllowedException;
import com.example.mspedidos.domain.exception.UndoRequestNotFoundException;
import com.example.mspedidos.domain.exception.UndoRequestPendingException;
import com.example.mspedidos.domain.model.AllocationUndoRequest;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.UndoRequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Deshacer una cobertura de inventario equivocada (spec 10.2c). Locks: orden -> unidad -> solicitud (subconjunto
 * del orden global orden -> cupo -> unidades, con la solicitud al final). ms_auth se consulta antes de abrir la
 * transaccion y ms-inventory despues del commit, nunca con locks tomados.
 */
@Service
public class AllocationUndoService implements AllocationUndoUseCase {

    static final String RESOURCE = "allocation-undo";
    static final String ACTION = "approve";
    /** Largo de {@code order_item_history.from_value/to_value}; el texto completo queda en la solicitud. */
    private static final int HISTORY_VALUE_MAX = 255;

    private final OrderRepositoryPort orderRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final AllocationUndoRequestRepositoryPort undoRepository;
    private final ItemIncidentRepositoryPort incidentRepository;
    private final AuthorizationPort authorizationPort;
    private final ActorProvider actorProvider;
    private final ItemHistoryRecorder history;
    private final InventoryReleaser releaser;
    private final TransactionTemplate tx;

    AllocationUndoService(OrderRepositoryPort orderRepository, OrderItemRepositoryPort itemRepository,
                          AllocationUndoRequestRepositoryPort undoRepository,
                          ItemIncidentRepositoryPort incidentRepository, AuthorizationPort authorizationPort,
                          ActorProvider actorProvider, ItemHistoryRecorder history, InventoryReleaser releaser,
                          PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.undoRepository = undoRepository;
        this.incidentRepository = incidentRepository;
        this.authorizationPort = authorizationPort;
        this.actorProvider = actorProvider;
        this.history = history;
        this.releaser = releaser;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public AllocationUndoRequest request(Long orderId, Long itemId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        String actor = actorProvider.currentActor();
        return tx.execute(status -> {
            lockOrder(orderId);
            OrderItem item = lockItem(orderId, itemId);
            if (!item.isInPostAllocationState()) {
                throw new UndoNotAllowedException("Solo se puede deshacer la cobertura de inventario mientras la "
                        + "unidad siga como la dejo la asignacion, actual: " + item.getStatus());
            }
            ensureNoPending(itemId);
            AllocationUndoRequest saved = undoRepository.save(
                    AllocationUndoRequest.open(itemId, item.getInventoryUnitId(), reason, actor));
            history.record(itemId, HistoryEventType.ALLOCATION_UNDO_REQUESTED, null, abbreviate(reason), actor);
            return saved;
        });
    }

    @Override
    public List<AllocationUndoRequest> search(UndoRequestStatus status) {
        return undoRepository.search(status);
    }

    @Override
    public AllocationUndoRequest approve(Long requestId, String note) {
        String actor = authorizedActor();
        OrderItem target = itemOf(requestId);
        Decision decision = tx.execute(status -> {
            lockOrder(target.getOrderId());
            OrderItem item = lockItem(target.getOrderId(), target.getId());
            AllocationUndoRequest request = lockRequest(requestId);
            request.approve(actor, note);
            if (!request.getInventoryUnitId().equals(item.getInventoryUnitId())) {
                throw new UndoNotAllowedException("La unidad " + item.getId() + " ya no esta cubierta por la unidad "
                        + "de inventario " + request.getInventoryUnitId());
            }
            String fromStatus = item.getStatus().name();
            ImporterType fromImporter = item.getImporterType();
            Long released = item.undoInventoryCoverage();
            OrderItem saved = itemRepository.save(item);
            AllocationUndoRequest decided = undoRepository.save(request);
            Long itemId = saved.getId();
            history.record(itemId, HistoryEventType.ALLOCATION_UNDONE, "unit:" + released,
                    abbreviate("request:" + requestId + " by " + actor + ": " + request.getReason()), actor);
            history.record(itemId, HistoryEventType.STATUS_CHANGED, fromStatus, saved.getStatus().name(), actor);
            if (fromImporter != saved.getImporterType()) {
                // simetria con la asignacion desde LIMA (UNASSIGNED/importador -> NOT_REQUIRED)
                history.record(itemId, HistoryEventType.IMPORTER_ASSIGNED, fromImporter.name(),
                        saved.getImporterType().name(), actor);
            }
            if (fromImporter != ImporterType.UNASSIGNED && saved.getImporterType() == ImporterType.UNASSIGNED) {
                incidentRepository.save(ItemIncident.importerPending(itemId,
                        "Asignacion de inventario deshecha; asignar importador", actor));
                history.record(itemId, HistoryEventType.INCIDENT_OPENED, null,
                        IncidentType.IMPORTER_ASSIGNMENT_PENDING.name(), actor);
            }
            return new Decision(decided, saved);
        });
        // deshacer ya confirmado: se libera en ms-inventory fuera de la transaccion; si falla, la marca queda
        // para el job de reintentos y la aprobacion sigue en pie
        releaser.release(decision.item(), actor);
        return decision.request();
    }

    @Override
    public AllocationUndoRequest reject(Long requestId, String note) {
        String actor = authorizedActor();
        OrderItem target = itemOf(requestId);
        return tx.execute(status -> {
            lockOrder(target.getOrderId());
            lockItem(target.getOrderId(), target.getId());
            AllocationUndoRequest request = lockRequest(requestId);
            request.reject(actor, note);
            AllocationUndoRequest saved = undoRepository.save(request);
            history.record(target.getId(), HistoryEventType.ALLOCATION_UNDO_REJECTED, null, abbreviate(note), actor);
            return saved;
        });
    }

    /** Solo un admin segun ms_auth; si ms_auth no responde se propaga {@code AuthUnavailableException} (503). */
    private String authorizedActor() {
        String actor = actorProvider.currentActor();
        if (!authorizationPort.isAllowed(actor, RESOURCE, ACTION)) {
            throw new ApprovalForbiddenException("El usuario " + actor + " no puede resolver solicitudes de deshacer "
                    + "asignaciones de inventario");
        }
        return actor;
    }

    /** Unidad de la solicitud (sin lock), para conocer su orden y tomar los locks en orden. */
    private OrderItem itemOf(Long requestId) {
        AllocationUndoRequest request = undoRepository.findById(requestId)
                .orElseThrow(() -> new UndoRequestNotFoundException(requestId));
        return itemRepository.findById(request.getOrderItemId())
                .orElseThrow(() -> new OrderItemNotFoundException(request.getOrderItemId()));
    }

    private void ensureNoPending(Long itemId) {
        undoRepository.findPendingByOrderItemId(itemId).ifPresent(pending -> {
            throw new UndoRequestPendingException("La unidad " + itemId
                    + " ya tiene una solicitud de deshacer pendiente: " + pending.getId());
        });
    }

    private AllocationUndoRequest lockRequest(Long requestId) {
        return undoRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new UndoRequestNotFoundException(requestId));
    }

    private OrderItem lockItem(Long orderId, Long itemId) {
        return itemRepository.findByIdAndOrderIdForUpdate(itemId, orderId)
                .orElseThrow(() -> new OrderItemNotFoundException(itemId));
    }

    private void lockOrder(Long orderId) {
        orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private static String abbreviate(String value) {
        return value.length() <= HISTORY_VALUE_MAX ? value : value.substring(0, HISTORY_VALUE_MAX - 3) + "...";
    }

    private record Decision(AllocationUndoRequest request, OrderItem item) {
    }
}
