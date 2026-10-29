package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.StatusChangeRequestUseCase;
import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.AuthorizationPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.exception.ApprovalForbiddenException;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.exception.StatusChangeRequestAlreadyDecidedException;
import com.example.mspedidos.domain.exception.StatusChangeRequestNotFoundException;
import com.example.mspedidos.domain.exception.StatusChangeRequestPendingException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Solicitudes de cambio de estado (spec 8.4). ms_auth se consulta antes de abrir la transaccion; dentro, el applier
 * del tipo toma sus locks (orden -> unidad, o compra -> unidades) y la fila de la solicitud va al final; los efectos
 * remotos ({@link StatusChangeApplier#afterCommit}) corren despues del commit, nunca con locks tomados.
 */
@Service
public class StatusChangeRequestService implements StatusChangeRequestUseCase {

    static final String RESOURCE = "status-change";
    static final String APPROVE = "approve";
    static final String APPLY_DIRECT = "apply-direct";
    /** Largo de {@code order_item_history.from_value/to_value}; el texto completo queda en la solicitud. */
    private static final int HISTORY_VALUE_MAX = 255;

    private final StatusChangeRequestRepositoryPort requestRepository;
    private final AuthorizationPort authorizationPort;
    private final ActorProvider actorProvider;
    private final ItemHistoryRecorder history;
    private final OrderItemRepositoryPort itemRepository;
    private final Map<StatusChangeRequestType, StatusChangeApplier> appliers;
    private final TransactionTemplate tx;

    StatusChangeRequestService(StatusChangeRequestRepositoryPort requestRepository, AuthorizationPort authorizationPort,
                               ActorProvider actorProvider, ItemHistoryRecorder history,
                               OrderItemRepositoryPort itemRepository, List<StatusChangeApplier> appliers,
                               PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.authorizationPort = authorizationPort;
        this.actorProvider = actorProvider;
        this.history = history;
        this.itemRepository = itemRepository;
        this.appliers = new EnumMap<>(StatusChangeRequestType.class);
        for (StatusChangeApplier applier : appliers) {
            if (this.appliers.putIfAbsent(applier.type(), applier) != null) {
                throw new IllegalStateException("Hay mas de un applier para " + applier.type());
            }
        }
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public StatusChangeRequest requestAllocationUndo(Long orderId, Long itemId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        String actor = actorProvider.currentActor();
        // foto sin lock: el applier vuelve a leer con lock y verifica que la unidad de inventario siga siendo esta
        OrderItem item = itemRepository.findByIdAndOrderId(itemId, orderId)
                .orElseThrow(() -> new OrderItemNotFoundException(itemId));
        return submit(StatusChangeRequest.openAllocationUndo(itemId, item.getInventoryUnitId(), reason, actor));
    }

    @Override
    public StatusChangeRequest submit(StatusChangeRequest draft) {
        StatusChangeApplier applier = applierFor(draft.getType());
        if (draft.getId() != null || draft.getStatus() != StatusChangeRequestStatus.PENDING) {
            throw new IllegalArgumentException("Solo se registra un borrador PENDING sin id");
        }
        String actor = draft.getRequestedBy();
        // falla cerrado: si ms_auth no responde se propaga AuthUnavailableException (503) sin tocar la BD
        boolean direct = authorizationPort.isAllowed(actor, RESOURCE, APPLY_DIRECT);
        StatusChangeRequest result = tx.execute(status -> {
            applier.lockAndValidate(draft);
            if (draft.getType() == StatusChangeRequestType.ALLOCATION_UNDO && draft.getOrderItemId() != null) {
                ensureNoPendingForItem(draft.getOrderItemId());
            }
            Long itemId = draft.getOrderItemId();
            if (direct) {
                draft.markAppliedDirectly(actor);
                StatusChangeRequest saved = requestRepository.save(draft);
                applier.apply(saved, actor);
                if (itemId != null) {
                    history.record(itemId, HistoryEventType.STATUS_CHANGE_APPLIED_DIRECTLY, null,
                            abbreviate(saved.getReason()), actor);
                }
                return saved;
            }
            StatusChangeRequest saved = requestRepository.save(draft);
            if (itemId != null) {
                history.record(itemId, HistoryEventType.STATUS_CHANGE_REQUESTED, null,
                        abbreviate(saved.getReason()), actor);
            }
            return saved;
        });
        if (direct) {
            applier.afterCommit(result, actor);
        }
        return result;
    }

    @Override
    public List<StatusChangeRequest> search(StatusChangeRequestStatus status, StatusChangeRequestType type) {
        return requestRepository.search(status, type);
    }

    @Override
    public StatusChangeRequest approve(Long id, String note) {
        String actor = authorizedApprover();
        StatusChangeRequest snapshot = findPending(id);
        StatusChangeApplier applier = applierFor(snapshot.getType());
        StatusChangeRequest approved = tx.execute(status -> {
            // locks del applier primero (orden -> unidad / compra -> unidades); la solicitud al final
            applier.lockAndValidate(snapshot);
            StatusChangeRequest request = lockRequest(id);
            request.approve(actor, note);
            StatusChangeRequest saved = requestRepository.save(request);
            applier.apply(saved, actor);
            return saved;
        });
        applier.afterCommit(approved, actor);
        return approved;
    }

    @Override
    public StatusChangeRequest reject(Long id, String note) {
        String actor = authorizedApprover();
        findPending(id);
        // rechazar no cambia unidades ni compras: solo se bloquea la fila de la solicitud (ultima en el orden
        // global), asi una solicitud cuyo efecto ya no es valido igual se puede rechazar
        return tx.execute(status -> {
            StatusChangeRequest request = lockRequest(id);
            request.reject(actor, note);
            StatusChangeRequest saved = requestRepository.save(request);
            if (saved.getOrderItemId() != null) {
                history.record(saved.getOrderItemId(), HistoryEventType.STATUS_CHANGE_REJECTED, null,
                        abbreviate(note), actor);
            }
            return saved;
        });
    }

    /** {@code status-change/approve} segun ms_auth; si ms_auth no responde se propaga {@code AuthUnavailableException}. */
    private String authorizedApprover() {
        String actor = actorProvider.currentActor();
        if (!authorizationPort.isAllowed(actor, RESOURCE, APPROVE)) {
            throw new ApprovalForbiddenException("El usuario " + actor
                    + " no puede resolver solicitudes de cambio de estado");
        }
        return actor;
    }

    /** Foto sin lock; corta antes de tomar locks si ya fue resuelta (se vuelve a verificar con la fila bloqueada). */
    private StatusChangeRequest findPending(Long id) {
        StatusChangeRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new StatusChangeRequestNotFoundException(id));
        if (request.getStatus() != StatusChangeRequestStatus.PENDING) {
            throw new StatusChangeRequestAlreadyDecidedException("La solicitud ya fue resuelta: " + request.getStatus());
        }
        return request;
    }

    private StatusChangeApplier applierFor(StatusChangeRequestType type) {
        StatusChangeApplier applier = appliers.get(type);
        if (applier == null) {
            throw new IllegalStateException("No hay applier para el tipo de solicitud " + type);
        }
        return applier;
    }

    private void ensureNoPendingForItem(Long itemId) {
        requestRepository.findPendingByOrderItemId(itemId).ifPresent(pending -> {
            throw new StatusChangeRequestPendingException("La unidad " + itemId
                    + " ya tiene una solicitud de cambio de estado pendiente: " + pending.getId());
        });
    }

    private StatusChangeRequest lockRequest(Long id) {
        return requestRepository.findByIdForUpdate(id).orElseThrow(() -> new StatusChangeRequestNotFoundException(id));
    }

    static String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= HISTORY_VALUE_MAX ? value : value.substring(0, HISTORY_VALUE_MAX - 3) + "...";
    }
}
