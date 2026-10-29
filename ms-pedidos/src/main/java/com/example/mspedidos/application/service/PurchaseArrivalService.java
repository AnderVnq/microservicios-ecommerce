package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.PurchaseRepositoryPort;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.exception.IdempotencyKeyReusedException;
import com.example.mspedidos.domain.exception.PurchaseNotFoundException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.PurchaseArrival;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Llegadas de una compra (spec 8.2). Locks: compra -> unidades de la compra (por id). Cubre las unidades
 * {@code PURCHASED} mas urgentes primero; lo que sobra es sobrante recibido. No consume cupo (el cupo se consume al
 * asignar importador). La tarea {@code REGISTER_ARRIVAL} se intenta enseguida despues del commit.
 */
@Slf4j
@Service
class PurchaseArrivalService {

    /** Compra afectada y si la respuesta repite una llegada ya registrada con la misma clave. */
    record Result(Long purchaseId, boolean replayed) {
    }

    private final PurchaseRepositoryPort purchaseRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final StatusChangeRequestRepositoryPort requestRepository;
    private final InventorySyncTaskRepositoryPort taskRepository;
    private final InventorySyncExecutor syncExecutor;
    private final ItemHistoryRecorder history;
    private final ActorProvider actorProvider;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    PurchaseArrivalService(PurchaseRepositoryPort purchaseRepository, OrderItemRepositoryPort itemRepository,
                           StatusChangeRequestRepositoryPort requestRepository,
                           InventorySyncTaskRepositoryPort taskRepository, InventorySyncExecutor syncExecutor,
                           ItemHistoryRecorder history, ActorProvider actorProvider, ObjectMapper json,
                           PlatformTransactionManager transactionManager) {
        this.purchaseRepository = purchaseRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.taskRepository = taskRepository;
        this.syncExecutor = syncExecutor;
        this.history = history;
        this.actorProvider = actorProvider;
        this.json = json;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    Result register(Long purchaseId, String idempotencyKey, String requestHash, int quantity) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > PurchaseService.MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("La cabecera Idempotency-Key es obligatoria (maximo "
                    + PurchaseService.MAX_KEY_LENGTH + " caracteres)");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("El hash de la peticion es obligatorio");
        }
        Optional<Result> replay = replayOf(purchaseId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        String actor = actorProvider.currentActor();
        Result result;
        try {
            result = tx.execute(status -> arrive(purchaseId, idempotencyKey, requestHash, quantity, actor));
        } catch (DataIntegrityViolationException e) {
            // otra peticion con la misma clave confirmo primero: la constraint unica decide
            return replayOf(purchaseId, idempotencyKey, requestHash).orElseThrow(() -> e);
        }
        if (result.replayed()) {
            return result;
        }
        // ya confirmada y fuera de toda transaccion: un fallo deja la tarea PENDING para el job
        try {
            syncExecutor.runForPurchase(purchaseId);
        } catch (RuntimeException e) {
            log.warn("No se pudo sincronizar la llegada de la compra {} con inventario: {}", purchaseId,
                    e.getMessage());
        }
        return new Result(purchaseId, false);
    }

    private Result arrive(Long purchaseId, String key, String hash, int quantity, String actor) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new PurchaseNotFoundException(purchaseId));
        // con el lock de la compra (READ COMMITTED) se ve la llegada que confirmo una peticion concurrente con la
        // misma clave: es una repeticion, no se toca nada (si no, podria fallar por cantidad antes del insert)
        Optional<Result> replay = replayOf(purchaseId, key, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        int setAside = requestRepository.sumPendingNotReceived(purchaseId);
        purchase.registerArrival(quantity, setAside);
        purchaseRepository.save(purchase);

        Map<Long, OrderItem> purchased = itemRepository.findByPurchaseIdForUpdate(purchaseId).stream()
                .filter(i -> i.getStatus() == OrderItemStatus.PURCHASED)
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        List<Long> filled = new ArrayList<>();
        if (!purchased.isEmpty()) {
            for (Long id : itemRepository.orderByUrgency(purchased.keySet())) {
                if (filled.size() == quantity) {
                    break;
                }
                OrderItem item = purchased.get(id);
                String from = item.getStatus().name();
                item.receiveFromPurchase();
                itemRepository.save(item);
                filled.add(id);
                history.record(id, HistoryEventType.PURCHASE_ARRIVED, from, "purchase:" + purchaseId, actor);
                history.record(id, HistoryEventType.STATUS_CHANGED, from, item.getStatus().name(), actor);
            }
        }
        PurchaseArrival arrival = purchaseRepository.saveArrival(PurchaseArrival.builder()
                .purchaseId(purchaseId)
                .idempotencyKey(key)
                .requestHash(hash)
                .quantity(quantity)
                .orderItemIds(filled)
                .surplusQuantity(quantity - filled.size())
                .createdBy(actor)
                .createdAt(Instant.now())
                .build());
        taskRepository.save(InventorySyncTask.builder()
                .purchaseId(purchaseId)
                .type(InventorySyncTaskType.REGISTER_ARRIVAL)
                .payload(json.writeValueAsString(new InventorySyncExecutor.ArrivalPayload(quantity, filled)))
                .idempotencyKey("purchase-" + purchaseId + "-arrival-" + arrival.getId())
                .status(InventorySyncTaskStatus.PENDING)
                .build());
        return new Result(purchaseId, false);
    }

    /** Las claves son globales: la misma clave en otra compra o con otro cuerpo es un reuso (422). */
    private Optional<Result> replayOf(Long purchaseId, String key, String hash) {
        return purchaseRepository.findArrivalByKey(key).map(existing -> {
            if (!existing.getPurchaseId().equals(purchaseId)) {
                throw new IdempotencyKeyReusedException("La Idempotency-Key " + key
                        + " ya se uso para otra compra");
            }
            if (!existing.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReusedException(
                        "La Idempotency-Key " + key + " ya se uso con otro cuerpo de peticion");
            }
            return new Result(purchaseId, true);
        });
    }
}
