package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.PurchaseRepositoryPort;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.exception.InventorySyncPendingException;
import com.example.mspedidos.domain.exception.NotEnoughPendingUnitsException;
import com.example.mspedidos.domain.exception.PurchaseNotFoundException;
import com.example.mspedidos.domain.exception.PurchaseQuantityExceededException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.PurchaseNonReceipt;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unidades de una compra que no llegaron (spec 8.3). Locks: compra -> unidades de la compra (por id); la fila de la
 * solicitud la bloquea el servicio al final. La seleccion se hace al aplicar, con el estado bloqueado de ese momento:
 * primero el sobrante pendiente, luego las unidades {@code PURCHASED} menos urgentes. La sincronizacion con
 * ms-inventory va por el outbox y se intenta despues del commit.
 */
@Slf4j
@Component
class NotReceivedApplier implements StatusChangeApplier {

    private final PurchaseRepositoryPort purchaseRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final StatusChangeRequestRepositoryPort requestRepository;
    private final InventorySyncTaskRepositoryPort taskRepository;
    private final InventorySyncExecutor syncExecutor;
    private final ItemHistoryRecorder history;
    private final ObjectMapper json;

    NotReceivedApplier(PurchaseRepositoryPort purchaseRepository, OrderItemRepositoryPort itemRepository,
                       StatusChangeRequestRepositoryPort requestRepository,
                       InventorySyncTaskRepositoryPort taskRepository, InventorySyncExecutor syncExecutor,
                       ItemHistoryRecorder history, ObjectMapper json) {
        this.purchaseRepository = purchaseRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.taskRepository = taskRepository;
        this.syncExecutor = syncExecutor;
        this.history = history;
        this.json = json;
    }

    /**
     * Ya existe una marca o solicitud de "no recibido" con la {@code Idempotency-Key} del borrador (visto con el
     * lock de la compra): el llamador repite el resultado original o responde reuso.
     */
    static class KeyAlreadyUsedException extends RuntimeException {
        KeyAlreadyUsedException(String key) {
            super("La Idempotency-Key " + key + " ya se uso");
        }
    }

    /** Compra y sus unidades bloqueadas (en ese orden) dentro de la transaccion del llamador. */
    record Locked(Purchase purchase, List<OrderItem> items) {

        /** Unidades fisicas pendientes que no cubren ninguna unidad de pedido. */
        int surplusPending() {
            long purchased = items.stream().filter(i -> i.getStatus() == OrderItemStatus.PURCHASED).count();
            return (int) Math.max(0, purchase.pending() - purchased);
        }
    }

    @Override
    public StatusChangeRequestType type() {
        return StatusChangeRequestType.PURCHASE_NOT_RECEIVED;
    }

    @Override
    public void lockAndValidate(StatusChangeRequest req) {
        Locked locked = lock(req.getPurchaseId());
        String key = req.getIdempotencyKey();
        if (req.getId() == null && key != null && (requestRepository.findByIdempotencyKey(key).isPresent()
                || purchaseRepository.findNonReceiptByKey(key).isPresent())) {
            throw new KeyAlreadyUsedException(key);
        }
        Purchase purchase = locked.purchase();
        ensureSynced(purchase);
        // la propia solicitud (si ya existe) no se aparta a si misma
        int setAside = requestRepository.sumPendingNotReceivedExcluding(purchase.getId(), req.getId());
        int available = purchase.pending() - setAside;
        if (req.getQuantity() > available) {
            String message = "La compra " + purchase.getId() + " solo tiene " + available
                    + " unidades pendientes disponibles; se pidieron " + req.getQuantity();
            if (req.getId() == null) {
                throw new PurchaseQuantityExceededException(message);
            }
            throw new NotEnoughPendingUnitsException(message);
        }
    }

    @Override
    public void apply(StatusChangeRequest req, String actor) {
        // locks ya tomados por lockAndValidate en esta transaccion; se releen para trabajar con el estado actual
        Locked locked = lock(req.getPurchaseId());
        // explicito: lo apartado por las otras solicitudes PENDING, nunca por esta
        int setAside = requestRepository.sumPendingNotReceivedExcluding(req.getPurchaseId(), req.getId());
        String label = req.getStatus() == StatusChangeRequestStatus.APPLIED_DIRECTLY
                ? "direct by " + actor + ": " + req.getReason()
                : "request:" + req.getId() + " by " + actor + ": " + req.getReason();
        applyLocked(locked, req.getQuantity(), setAside, req.getReason(), req.getId(), label, actor, null, null);
    }

    @Override
    public void afterCommit(StatusChangeRequest req, String actor) {
        syncAfterCommit(req.getPurchaseId());
    }

    /** Bloquea la compra y luego sus unidades (por id). */
    Locked lock(Long purchaseId) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new PurchaseNotFoundException(purchaseId));
        return new Locked(purchase, itemRepository.findByPurchaseIdForUpdate(purchaseId));
    }

    static void ensureSynced(Purchase purchase) {
        if (purchase.getInventoryEntryId() == null) {
            throw new InventorySyncPendingException("La compra " + purchase.getId()
                    + " aun no se sincronizo con inventario; intente mas tarde");
        }
    }

    /**
     * Aplica la marca con los locks ya tomados: sobrante primero, luego las unidades {@code PURCHASED} menos urgentes
     * (vuelven a {@code PROCESSING}); registro en {@code purchase_non_receipts} y tarea {@code REGISTER_NOT_RECEIVED}.
     *
     * @param historyLabel   valor "hacia" del historial {@code PURCHASE_NOT_RECEIVED} de cada unidad revertida
     * @param idempotencyKey clave de la marca directa (sin solicitud); null si viene de una solicitud
     */
    PurchaseNonReceipt applyLocked(Locked locked, int quantity, int setAside, String reason, Long requestId,
                                   String historyLabel, String actor, String idempotencyKey, String requestHash) {
        Purchase purchase = locked.purchase();
        Long purchaseId = purchase.getId();
        // el sobrante se calcula con lo pendiente de antes de esta marca
        int fromSurplus = Math.min(quantity, locked.surplusPending());
        // valida contra pendiente - apartado antes de tocar unidades
        purchase.registerNotReceived(quantity, setAside);
        List<OrderItem> reverted = leastUrgentPurchased(locked.items(), quantity - fromSurplus);

        List<Long> revertedIds = new ArrayList<>();
        String to = StatusChangeRequestService.abbreviate(historyLabel);
        for (OrderItem item : reverted) {
            String from = item.getStatus().name();
            item.revertNotReceived();
            itemRepository.save(item);
            revertedIds.add(item.getId());
            history.record(item.getId(), HistoryEventType.PURCHASE_NOT_RECEIVED, "purchase:" + purchaseId, to,
                    actor);
            history.record(item.getId(), HistoryEventType.STATUS_CHANGED, from, OrderItemStatus.PROCESSING.name(),
                    actor);
        }
        purchaseRepository.save(purchase);
        PurchaseNonReceipt saved = purchaseRepository.saveNonReceipt(PurchaseNonReceipt.builder()
                .purchaseId(purchaseId)
                .quantity(quantity)
                .orderItemIds(revertedIds)
                .surplusQuantity(fromSurplus)
                .reason(reason)
                .statusChangeRequestId(requestId)
                .appliedBy(actor)
                .appliedAt(Instant.now())
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .build());
        taskRepository.save(InventorySyncTask.builder()
                .purchaseId(purchaseId)
                .type(InventorySyncTaskType.REGISTER_NOT_RECEIVED)
                .payload(json.writeValueAsString(
                        new InventorySyncExecutor.NotReceivedPayload(quantity, revertedIds, reason)))
                .idempotencyKey("purchase-" + purchaseId + "-not-received-" + saved.getId())
                .status(InventorySyncTaskStatus.PENDING)
                .build());
        return saved;
    }

    /** Fuera de toda transaccion; un fallo deja la tarea PENDING para el job. */
    void syncAfterCommit(Long purchaseId) {
        try {
            syncExecutor.runForPurchase(purchaseId);
        } catch (RuntimeException e) {
            log.warn("No se pudo sincronizar la compra {} con inventario: {}", purchaseId, e.getMessage());
        }
    }

    /** Las {@code count} unidades {@code PURCHASED} menos urgentes (la urgencia inversa exacta). */
    private List<OrderItem> leastUrgentPurchased(List<OrderItem> items, int count) {
        if (count <= 0) {
            return List.of();
        }
        Map<Long, OrderItem> purchased = items.stream()
                .filter(i -> i.getStatus() == OrderItemStatus.PURCHASED)
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        if (purchased.size() < count) {
            // no deberia pasar: la cantidad ya se valido contra lo pendiente y el sobrante
            throw new NotEnoughPendingUnitsException("No hay " + count + " unidades de pedido para marcar");
        }
        List<Long> byUrgency = new ArrayList<>(itemRepository.orderByUrgency(purchased.keySet()));
        Collections.reverse(byUrgency);
        return byUrgency.subList(0, count).stream().map(purchased::get).toList();
    }
}
