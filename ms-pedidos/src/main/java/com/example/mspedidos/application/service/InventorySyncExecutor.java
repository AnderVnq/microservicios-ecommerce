package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.InventoryPort;
import com.example.mspedidos.application.port.out.InventoryPort.CreatePurchaseEntry;
import com.example.mspedidos.application.port.out.InventoryPort.PurchaseEntryResult;
import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.PurchaseRepositoryPort;
import com.example.mspedidos.domain.exception.InventorySyncRejectedException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.PurchaseNotFoundException;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Purchase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ejecuta el outbox {@code inventory_sync_tasks} hacia ms-inventory (spec 6.6). Las tareas de una compra se ejecutan
 * en orden de id; la primera que no termina detiene las siguientes de esa compra (las demas compras siguen). La
 * llamada remota ocurre sin locks; el resultado se aplica en una transaccion corta READ_COMMITTED con locks
 * compra -> unidades de pedido (por id) -> tarea, y solo si la tarea sigue {@code PENDING}. Error transitorio ->
 * sigue {@code PENDING} (+1 intento); rechazo definitivo -> {@code FAILED} y log ERROR. Los rechazos
 * {@link #WAIT_CODES} son esperas entre colas: transitorios hasta {@link #MAX_CONFLICT_ATTEMPTS} intentos. Las
 * liberaciones pendientes de unidades de la compra se procesan antes que sus tareas y, si quedan pendientes, las
 * tareas esperan. Un fallo al aplicar el resultado local tambien cuenta como intento.
 * <p>
 * Debe llamarse fuera de toda transaccion ({@link IllegalStateException} si no). No lanza por fallos de inventario
 * ni de BD de una tarea: quedan registrados en la tarea o en el log para la siguiente pasada del job.
 */
@Slf4j
@Component
class InventorySyncExecutor {

    /** Autor del historial que escribe el executor/job (igual que el job de liberaciones). */
    static final String ACTOR = "system";

    /**
     * Codigos remotos que en tareas de compras significan "esperar a otra cola" (una liberacion aun pendiente, una
     * no recibida de otra compra aun sin enviar, una asignacion de stock concurrente): se reintentan.
     */
    static final Set<String> WAIT_CODES = Set.of("NOT_ENOUGH_PURCHASE_UNITS", "ALLOCATION_CONFLICT");

    /** Intentos (de la tarea) tras los cuales un {@link #WAIT_CODES} deja de esperar y la tarea queda FAILED. */
    static final int MAX_CONFLICT_ATTEMPTS = 10;

    private final InventoryPort inventoryPort;
    private final InventorySyncTaskRepositoryPort taskRepository;
    private final PurchaseRepositoryPort purchaseRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final InventoryReleaser releaser;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    InventorySyncExecutor(InventoryPort inventoryPort, InventorySyncTaskRepositoryPort taskRepository,
                          PurchaseRepositoryPort purchaseRepository, OrderItemRepositoryPort itemRepository,
                          InventoryReleaser releaser, ObjectMapper json,
                          PlatformTransactionManager transactionManager) {
        this.inventoryPort = inventoryPort;
        this.taskRepository = taskRepository;
        this.purchaseRepository = purchaseRepository;
        this.itemRepository = itemRepository;
        this.releaser = releaser;
        this.json = json;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Payload de {@code REGISTER_ARRIVAL}. */
    public record ArrivalPayload(int quantity, List<Long> orderItemIds) {
    }

    /** Payload de {@code REGISTER_NOT_RECEIVED}. */
    public record NotReceivedPayload(int quantity, List<Long> orderItemIds, String reason) {
    }

    /** Recorre las compras con tareas {@code PENDING} (en orden de su primera tarea pendiente). */
    void runAllPending() {
        assertNoTransaction();
        Set<Long> purchaseIds = new LinkedHashSet<>();
        try {
            taskRepository.findPendingOrdered().forEach(t -> purchaseIds.add(t.getPurchaseId()));
        } catch (RuntimeException e) {
            log.warn("No se pudieron leer las tareas de sincronizacion pendientes: {}", e.getMessage());
            return;
        }
        for (Long purchaseId : purchaseIds) {
            runForPurchase(purchaseId);
        }
    }

    /**
     * Ejecuta en orden las tareas pendientes de la compra hasta la primera que no termine. Antes libera en
     * ms-inventory las unidades de la compra con liberacion pendiente: mientras alguna siga pendiente las tareas de
     * la compra esperan (ms-inventory aun ve esa unidad reservada y rechazaria una llegada/no recibida por falta de
     * unidades libres).
     */
    void runForPurchase(Long purchaseId) {
        assertNoTransaction();
        if (!releasePendingItems(purchaseId)) {
            return;
        }
        List<InventorySyncTask> tasks;
        try {
            tasks = taskRepository.findByPurchaseId(purchaseId);
        } catch (RuntimeException e) {
            log.warn("No se pudieron leer las tareas de sincronizacion de la compra {}: {}", purchaseId, e.getMessage());
            return;
        }
        for (InventorySyncTask task : tasks) {
            if (task.getStatus() == InventorySyncTaskStatus.DONE) {
                continue;
            }
            if (task.getStatus() != InventorySyncTaskStatus.PENDING || !run(task)) {
                // FAILED bloquea la compra hasta el reintento manual; un fallo transitorio espera al job
                return;
            }
        }
    }

    /** @return true si ninguna unidad de pedido de la compra quedo con liberacion pendiente */
    private boolean releasePendingItems(Long purchaseId) {
        List<OrderItem> pending;
        try {
            pending = itemRepository.findByPurchaseId(purchaseId).stream()
                    .filter(OrderItem::isInventoryReleasePending).toList();
        } catch (RuntimeException e) {
            log.warn("No se pudieron leer las liberaciones pendientes de la compra {}: {}", purchaseId, e.getMessage());
            return false;
        }
        return releaseAll(purchaseId, pending);
    }

    /** Libera cada unidad (fuera de transaccion); false si alguna sigue pendiente. */
    private boolean releaseAll(Long purchaseId, List<OrderItem> items) {
        boolean allReleased = true;
        for (OrderItem item : items) {
            OrderItem after = releaser.release(item, ACTOR);
            if (after.isInventoryReleasePending()) {
                allReleased = false;
            }
        }
        if (!allReleased) {
            log.warn("La compra {} tiene liberaciones en ms-inventory pendientes; sus tareas de sincronizacion "
                    + "esperan a la siguiente pasada", purchaseId);
        }
        return allReleased;
    }

    /** Reintento manual: las tareas {@code FAILED} de la compra vuelven a {@code PENDING} y se ejecutan. */
    void retryFailed(Long purchaseId) {
        assertNoTransaction();
        tx.executeWithoutResult(status -> {
            purchaseRepository.findByIdForUpdate(purchaseId)
                    .orElseThrow(() -> new PurchaseNotFoundException(purchaseId));
            for (InventorySyncTask task : taskRepository.findByPurchaseId(purchaseId)) {
                if (task.getStatus() != InventorySyncTaskStatus.FAILED) {
                    continue;
                }
                taskRepository.findByIdForUpdate(task.getId())
                        .filter(locked -> locked.getStatus() == InventorySyncTaskStatus.FAILED)
                        .ifPresent(locked -> {
                            locked.resetForRetry();
                            taskRepository.save(locked);
                        });
            }
        });
        runForPurchase(purchaseId);
    }

    /** @return true si la tarea quedo {@code DONE} (se puede seguir con la siguiente de la compra) */
    private boolean run(InventorySyncTask task) {
        PurchaseEntryResult entry;
        try {
            entry = call(task);
        } catch (InventoryUnavailableException e) {
            log.warn("Sincronizacion con inventario pendiente (tarea {} {}, compra {}): {}",
                    task.getId(), task.getType(), task.getPurchaseId(), e.getMessage());
            recordFailure(task, e.getMessage(), false);
            return false;
        } catch (InventorySyncRejectedException e) {
            if (WAIT_CODES.contains(e.getCode())) {
                return recordWait(task, e);
            }
            log.error("ms-inventory rechazo la tarea {} {} de la compra {} ({}); queda FAILED hasta reintento manual: {}",
                    task.getId(), task.getType(), task.getPurchaseId(), e.getCode(), e.getMessage());
            recordFailure(task, e.getCode() + ": " + e.getMessage(), true);
            return false;
        } catch (RuntimeException e) {
            // payload ilegible u otro error de programacion: reintentar no lo arregla
            log.error("La tarea {} {} de la compra {} no se pudo ejecutar; queda FAILED: {}",
                    task.getId(), task.getType(), task.getPurchaseId(), e.toString());
            recordFailure(task, e.toString(), true);
            return false;
        }
        List<OrderItem> toRelease;
        try {
            toRelease = tx.execute(status -> apply(task, entry));
        } catch (RuntimeException e) {
            // la llamada es idempotente por clave: la siguiente pasada la repite y aplica el resultado
            log.warn("ms-inventory proceso la tarea {} de la compra {} pero no se pudo aplicar el resultado: {}",
                    task.getId(), task.getPurchaseId(), e.getMessage());
            recordFailure(task, "apply: " + e, false);
            return false;
        }
        if (toRelease == null) {
            // otra ejecucion concurrente ya resolvio la tarea
            return false;
        }
        // si alguna liberacion queda pendiente, las tareas siguientes de la compra esperan (ver runForPurchase)
        return releaseAll(task.getPurchaseId(), toRelease);
    }

    /**
     * Espera entre colas: sigue {@code PENDING} con un intento mas; al llegar a {@link #MAX_CONFLICT_ATTEMPTS}
     * intentos queda {@code FAILED} (log ERROR) para revision manual.
     *
     * @return siempre false (la compra no sigue en esta pasada)
     */
    private boolean recordWait(InventorySyncTask task, InventorySyncRejectedException e) {
        String error = e.getCode() + ": " + e.getMessage();
        Boolean failed = recordFailure(task, error, Mode.WAIT);
        if (Boolean.TRUE.equals(failed)) {
            log.error("ms-inventory sigue rechazando la tarea {} {} de la compra {} ({}) tras {} intentos; queda FAILED "
                            + "hasta reintento manual: {}", task.getId(), task.getType(), task.getPurchaseId(),
                    e.getCode(), MAX_CONFLICT_ATTEMPTS, e.getMessage());
        } else {
            log.warn("ms-inventory aun no puede procesar la tarea {} {} de la compra {} ({}); se reintentara: {}",
                    task.getId(), task.getType(), task.getPurchaseId(), e.getCode(), e.getMessage());
        }
        return false;
    }

    /** Llamada remota; solo CREATE_ENTRY devuelve resultado que aplicar. */
    private PurchaseEntryResult call(InventorySyncTask task) {
        String key = task.getIdempotencyKey();
        Long purchaseId = task.getPurchaseId();
        switch (task.getType()) {
            case CREATE_ENTRY -> {
                return inventoryPort.createPurchaseEntry(key,
                        json.readValue(task.getPayload(), CreatePurchaseEntry.class));
            }
            case REGISTER_ARRIVAL -> {
                ArrivalPayload p = json.readValue(task.getPayload(), ArrivalPayload.class);
                inventoryPort.registerPurchaseArrival(key, purchaseId, p.quantity(), ids(p.orderItemIds()));
                return null;
            }
            case REGISTER_NOT_RECEIVED -> {
                NotReceivedPayload p = json.readValue(task.getPayload(), NotReceivedPayload.class);
                inventoryPort.registerPurchaseNotReceived(key, purchaseId, p.quantity(), ids(p.orderItemIds()),
                        p.reason());
                return null;
            }
            default -> throw new IllegalStateException("Tipo de tarea desconocido: " + task.getType());
        }
    }

    /**
     * Aplica el resultado dentro de la transaccion (locks compra -> unidades -> tarea).
     *
     * @return unidades a liberar despues del commit, o null si la tarea ya no estaba PENDING
     */
    private List<OrderItem> apply(InventorySyncTask task, PurchaseEntryResult entry) {
        List<OrderItem> toRelease = new ArrayList<>();
        Purchase purchase = null;
        List<OrderItem> items = List.of();
        if (entry != null) {
            purchase = purchaseRepository.findByIdForUpdate(task.getPurchaseId())
                    .orElseThrow(() -> new PurchaseNotFoundException(task.getPurchaseId()));
            items = entry.unitByOrderItem().isEmpty() ? List.of()
                    : itemRepository.findByIdsForUpdate(entry.unitByOrderItem().keySet());
        }
        InventorySyncTask locked = taskRepository.findByIdForUpdate(task.getId()).orElse(null);
        if (locked == null || locked.getStatus() != InventorySyncTaskStatus.PENDING) {
            return null;
        }
        if (purchase != null) {
            purchase.linkInventoryEntry(entry.entryId());
            purchaseRepository.save(purchase);
            Map<Long, Long> unitByItem = entry.unitByOrderItem();
            for (OrderItem item : items) {
                Long unitId = unitByItem.get(item.getId());
                if (linkOrFlag(item, task.getPurchaseId(), unitId)) {
                    OrderItem saved = itemRepository.save(item);
                    if (saved.isInventoryReleasePending()) {
                        toRelease.add(saved);
                    }
                }
            }
            if (items.size() != unitByItem.size()) {
                log.error("ms-inventory reservo unidades para unidades de pedido inexistentes (compra {}): {}",
                        task.getPurchaseId(), unitByItem);
            }
        }
        locked.markDone();
        taskRepository.save(locked);
        return toRelease;
    }

    /** @return true si la unidad de pedido cambio y hay que guardarla */
    private boolean linkOrFlag(OrderItem item, Long purchaseId, Long unitId) {
        boolean ours = purchaseId.equals(item.getPurchaseId());
        if (ours && item.getStatus() != OrderItemStatus.CANCELLED) {
            if (unitId.equals(item.getInventoryUnitId())) {
                return false;
            }
            item.linkPurchaseUnit(unitId);
            return true;
        }
        if (item.getStatus() == OrderItemStatus.CANCELLED && item.getInventoryUnitId() == null
                && !item.isInventoryReleasePending()) {
            // cancelada antes de sincronizar: la reserva queda como sobrante
            item.flagReleaseOfUnlinkedPurchaseUnit(unitId);
            return true;
        }
        if (item.isInventoryReleasePending() && unitId.equals(item.getPendingReleaseUnitId())) {
            return false;
        }
        log.error("La unidad de inventario {} reservada para la unidad de pedido {} (compra {}) no se pudo vincular "
                        + "ni marcar para liberar (estado {}, compra {}, unidad {}); revisar manualmente",
                unitId, item.getId(), purchaseId, item.getStatus(), item.getPurchaseId(), item.getInventoryUnitId());
        return false;
    }

    /** Como registrar un fallo: transitorio, definitivo, o espera entre colas (FAILED al agotar los intentos). */
    private enum Mode { TRANSIENT, PERMANENT, WAIT }

    /** @return true si la tarea quedo FAILED; null si no se pudo registrar */
    private Boolean recordFailure(InventorySyncTask task, String error, boolean permanent) {
        return recordFailure(task, error, permanent ? Mode.PERMANENT : Mode.TRANSIENT);
    }

    private Boolean recordFailure(InventorySyncTask task, String error, Mode mode) {
        try {
            return tx.execute(status -> taskRepository.findByIdForUpdate(task.getId())
                    .filter(locked -> locked.getStatus() == InventorySyncTaskStatus.PENDING)
                    .map(locked -> {
                        String trimmed = truncate(error);
                        boolean fail = mode == Mode.PERMANENT
                                || (mode == Mode.WAIT && locked.getAttempts() + 1 >= MAX_CONFLICT_ATTEMPTS);
                        if (fail) {
                            locked.markFailed(trimmed);
                        } else {
                            locked.recordAttempt(trimmed);
                        }
                        taskRepository.save(locked);
                        return fail;
                    })
                    .orElse(false));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el fallo de la tarea {}: {}", task.getId(), e.getMessage());
            return null;
        }
    }

    private static List<Long> ids(List<Long> ids) {
        return ids == null ? List.of() : ids;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }

    private static void assertNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La sincronizacion con ms-inventory no puede ocurrir dentro de una transaccion");
        }
    }
}
