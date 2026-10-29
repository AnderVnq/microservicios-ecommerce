package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.out.InventoryPort.CreatePurchaseEntry;
import com.example.mspedidos.application.port.out.InventoryPort.PurchaseEntryResult;
import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.application.port.out.PurchaseRepositoryPort;
import com.example.mspedidos.domain.exception.InventorySyncRejectedException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Purchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class InventorySyncExecutorTest extends ServiceTestSupport {

    @Autowired
    InventorySyncExecutor executor;
    @Autowired
    InventorySyncJob job;
    @Autowired
    InventorySyncTaskRepositoryPort taskRepository;
    @Autowired
    PurchaseRepositoryPort purchaseRepository;
    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;
    @Autowired
    ObjectMapper json;
    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanPurchases() {
        jdbc.update("delete from inventory_sync_tasks");
        jdbc.update("delete from purchase_arrivals");
        jdbc.update("delete from purchase_non_receipts");
        jdbc.update("delete from purchases");
    }

    private Purchase purchase(int quantity) {
        return purchaseRepository.save(Purchase.builder().idempotencyKey("key-" + uniqueExternalId())
                .requestHash("h").supplierId(7L).sku("SKU-1").conditionCode("NEW").quantity(quantity)
                .unitCost(new BigDecimal("12.50")).costCurrency("USD").supplierPurchaseId("SUP-1")
                .expectedArrivalDate(LocalDate.of(2026, 11, 15)).createdBy("op-1").createdAt(Instant.now())
                .build());
    }

    /** Orden con {@code count} unidades cubiertas por la compra (PURCHASED, sin unidad de inventario). */
    private List<OrderItem> coveredItems(Purchase purchase, int count) {
        stubHolder(1L, dni("111"));
        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(count, "10.00")));
        return view.items().stream().map(item -> {
            item.coverByPurchase(purchase.getId());
            return itemRepository.save(item);
        }).toList();
    }

    private CreatePurchaseEntry entryBody(Purchase p, List<Long> reservations) {
        return new CreatePurchaseEntry(p.getId(), p.getSupplierId(), p.getSku(), p.getConditionCode(),
                p.getQuantity(), p.getUnitCost(), p.getCostCurrency(), p.getExpectedArrivalDate(), reservations);
    }

    private InventorySyncTask task(Long purchaseId, InventorySyncTaskType type, Object payload, String key) {
        return taskRepository.save(InventorySyncTask.builder().purchaseId(purchaseId).type(type)
                .payload(json.writeValueAsString(payload)).idempotencyKey(key)
                .status(InventorySyncTaskStatus.PENDING).build());
    }

    private InventorySyncTask createEntryTask(Purchase p, List<Long> reservations) {
        return task(p.getId(), InventorySyncTaskType.CREATE_ENTRY, entryBody(p, reservations),
                "purchase-" + p.getId() + "-entry");
    }

    private InventorySyncTask arrivalTask(Purchase p, int quantity, List<Long> itemIds) {
        return task(p.getId(), InventorySyncTaskType.REGISTER_ARRIVAL,
                new InventorySyncExecutor.ArrivalPayload(quantity, itemIds),
                "purchase-" + p.getId() + "-arrival-" + quantity);
    }

    private InventorySyncTask stored(InventorySyncTask t) {
        return taskRepository.findByPurchaseId(t.getPurchaseId()).stream()
                .filter(x -> x.getId().equals(t.getId())).findFirst().orElseThrow();
    }

    private OrderItem reload(OrderItem item) {
        return itemRepository.findById(item.getId()).orElseThrow();
    }

    @Test
    void createEntry_linksUnitsToPurchasedItems() {
        Purchase p = purchase(3);
        List<OrderItem> items = coveredItems(p, 2);
        OrderItem a = items.get(0);
        OrderItem b = items.get(1);
        InventorySyncTask t = createEntryTask(p, List.of(b.getId(), a.getId()));
        when(inventoryPort.createPurchaseEntry(anyString(), any()))
                .thenReturn(new PurchaseEntryResult(9L, Map.of(b.getId(), 101L, a.getId(), 102L)));

        executor.runForPurchase(p.getId());

        verify(inventoryPort).createPurchaseEntry("purchase-" + p.getId() + "-entry",
                entryBody(p, List.of(b.getId(), a.getId())));
        assertThat(purchaseRepository.findById(p.getId()).orElseThrow().getInventoryEntryId()).isEqualTo(9L);
        assertThat(reload(a).getInventoryUnitId()).isEqualTo(102L);
        assertThat(reload(b).getInventoryUnitId()).isEqualTo(101L);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        InventorySyncTask done = stored(t);
        assertThat(done.getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
        assertThat(done.getDoneAt()).isNotNull();
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    /** Unidad que ya llego al almacen antes de sincronizar la entrada: tambien se vincula (no se libera). */
    @Test
    void createEntry_itemAlreadyArrived_stillLinked() {
        Purchase p = purchase(1);
        OrderItem a = coveredItems(p, 1).get(0);
        a.receiveFromPurchase();
        itemRepository.save(a);
        createEntryTask(p, List.of(a.getId()));
        when(inventoryPort.createPurchaseEntry(anyString(), any()))
                .thenReturn(new PurchaseEntryResult(9L, Map.of(a.getId(), 101L)));

        executor.runForPurchase(p.getId());

        assertThat(reload(a).getInventoryUnitId()).isEqualTo(101L);
        assertThat(reload(a).isInventoryReleasePending()).isFalse();
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void cancelBeforeSync_reservationReleasedAfterSync() {
        Purchase p = purchase(2);
        List<OrderItem> items = coveredItems(p, 2);
        OrderItem a = items.get(0);
        OrderItem b = items.get(1);
        InventorySyncTask t = createEntryTask(p, List.of(a.getId(), b.getId()));
        itemUseCase.changeStatus(b.getOrderId(), b.getId(), OrderItemStatus.CANCELLED, null, null);
        assertThat(reload(b).isInventoryReleasePending()).isFalse();
        when(inventoryPort.createPurchaseEntry(anyString(), any()))
                .thenReturn(new PurchaseEntryResult(9L, Map.of(a.getId(), 101L, b.getId(), 102L)));

        executor.runForPurchase(p.getId());

        var order = inOrder(inventoryPort);
        order.verify(inventoryPort).createPurchaseEntry(anyString(), any());
        order.verify(inventoryPort).release(102L, b.getId());
        verify(inventoryPort, never()).release(eq(101L), anyLong());
        assertThat(reload(a).getInventoryUnitId()).isEqualTo(101L);
        OrderItem storedB = reload(b);
        assertThat(storedB.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(storedB.getInventoryUnitId()).isNull();
        assertThat(storedB.isInventoryReleasePending()).isFalse();
        assertThat(storedB.getPendingReleaseUnitId()).isNull();
        OrderItemHistoryEntry released = historyRepository.findByOrderItemId(b.getId()).stream()
                .filter(h -> h.eventType() == HistoryEventType.INVENTORY_RELEASED).findFirst().orElseThrow();
        assertThat(released.fromValue()).isEqualTo("unit:102");
        assertThat(released.performedBy()).isEqualTo("system");
        assertThat(stored(t).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
    }

    /** Si la liberacion falla, la marca queda para el job de liberaciones; la tarea igual queda DONE. */
    @Test
    void cancelBeforeSync_releaseFails_flagKeptForReleaseJob() {
        Purchase p = purchase(1);
        OrderItem a = coveredItems(p, 1).get(0);
        InventorySyncTask t = createEntryTask(p, List.of(a.getId()));
        itemUseCase.changeStatus(a.getOrderId(), a.getId(), OrderItemStatus.CANCELLED, null, null);
        when(inventoryPort.createPurchaseEntry(anyString(), any()))
                .thenReturn(new PurchaseEntryResult(9L, Map.of(a.getId(), 101L)));
        doThrow(new InventoryUnavailableException("caido")).when(inventoryPort).release(anyLong(), anyLong());

        executor.runForPurchase(p.getId());

        OrderItem stored = reload(a);
        assertThat(stored.isInventoryReleasePending()).isTrue();
        assertThat(stored.getPendingReleaseUnitId()).isEqualTo(101L);
        assertThat(itemRepository.findReleasePending()).extracting(OrderItem::getId).containsExactly(a.getId());
        assertThat(stored(t).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
    }

    @Test
    void transientFailure_staysPending_laterTasksNotRun() {
        Purchase p = purchase(2);
        OrderItem a = coveredItems(p, 1).get(0);
        InventorySyncTask entry = createEntryTask(p, List.of(a.getId()));
        InventorySyncTask arrival = arrivalTask(p, 1, List.of(a.getId()));
        when(inventoryPort.createPurchaseEntry(anyString(), any()))
                .thenThrow(new InventoryUnavailableException("ms-inventory no disponible"));

        executor.runForPurchase(p.getId());

        verify(inventoryPort, never()).registerPurchaseArrival(anyString(), anyLong(), anyInt(), anyList());
        InventorySyncTask storedEntry = stored(entry);
        assertThat(storedEntry.getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
        assertThat(storedEntry.getAttempts()).isEqualTo(1);
        assertThat(storedEntry.getLastError()).contains("ms-inventory no disponible");
        assertThat(stored(arrival).getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
        assertThat(stored(arrival).getAttempts()).isZero();
        assertThat(reload(a).getInventoryUnitId()).isNull();

        // ms-inventory vuelve: la siguiente pasada del job ejecuta ambas en orden
        clearInvocations(inventoryPort);
        doReturn(new PurchaseEntryResult(9L, Map.of(a.getId(), 101L)))
                .when(inventoryPort).createPurchaseEntry(anyString(), any());
        job.run();

        var order = inOrder(inventoryPort);
        order.verify(inventoryPort).createPurchaseEntry(anyString(), any());
        order.verify(inventoryPort).registerPurchaseArrival(
                "purchase-" + p.getId() + "-arrival-1", p.getId(), 1, List.of(a.getId()));
        assertThat(stored(entry).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
        assertThat(stored(entry).getAttempts()).isEqualTo(1);
        assertThat(stored(arrival).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
    }

    @Test
    void permanentFailure_failed_blocksPurchase_otherPurchasesRun() {
        Purchase p1 = purchase(1);
        Purchase p2 = purchase(1);
        InventorySyncTask entry1 = createEntryTask(p1, List.of());
        InventorySyncTask arrival1 = arrivalTask(p1, 1, List.of());
        InventorySyncTask entry2 = createEntryTask(p2, List.of());
        when(inventoryPort.createPurchaseEntry(eq("purchase-" + p1.getId() + "-entry"), any()))
                .thenThrow(new InventorySyncRejectedException("NOT_FOUND", "Condicion no encontrada: NEW"));
        when(inventoryPort.createPurchaseEntry(eq("purchase-" + p2.getId() + "-entry"), any()))
                .thenReturn(new PurchaseEntryResult(20L, Map.of()));

        executor.runAllPending();

        InventorySyncTask failed = stored(entry1);
        assertThat(failed.getStatus()).isEqualTo(InventorySyncTaskStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("NOT_FOUND");
        assertThat(stored(arrival1).getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
        assertThat(stored(entry2).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
        assertThat(purchaseRepository.findById(p2.getId()).orElseThrow().getInventoryEntryId()).isEqualTo(20L);
        assertThat(purchaseRepository.findById(p1.getId()).orElseThrow().getInventoryEntryId()).isNull();
        verify(inventoryPort, never()).registerPurchaseArrival(anyString(), anyLong(), anyInt(), anyList());

        // la tarea FAILED sigue bloqueando la compra en pasadas posteriores (no se reintenta sola)
        clearInvocations(inventoryPort);
        executor.runAllPending();
        executor.runForPurchase(p1.getId());
        verifyNoMoreInteractions(inventoryPort);

        // reintento manual: FAILED -> PENDING conservando intentos, y se ejecuta en orden
        doReturn(new PurchaseEntryResult(10L, Map.of()))
                .when(inventoryPort).createPurchaseEntry(eq("purchase-" + p1.getId() + "-entry"), any());
        executor.retryFailed(p1.getId());

        assertThat(stored(entry1).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
        assertThat(stored(entry1).getAttempts()).isEqualTo(1);
        assertThat(stored(arrival1).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
        verify(inventoryPort).registerPurchaseArrival(anyString(), eq(p1.getId()), eq(1), eq(List.of()));
    }

    /**
     * Unidad de pedido cubierta y vinculada que se cancela mientras ms-inventory no libera: la llegada por toda la
     * cantidad pendiente no se envia mientras la liberacion siga pendiente (ms-inventory responderia
     * NOT_ENOUGH_PURCHASE_UNITS). Cuando la liberacion pasa, la siguiente pasada libera primero y luego la llegada.
     */
    @Test
    void pendingReleaseOfPurchaseItem_blocksTasksUntilReleased() {
        Purchase p = purchase(2);
        OrderItem a = coveredItems(p, 1).get(0);
        createEntryTask(p, List.of(a.getId()));
        when(inventoryPort.createPurchaseEntry(anyString(), any()))
                .thenReturn(new PurchaseEntryResult(9L, Map.of(a.getId(), 101L)));
        executor.runForPurchase(p.getId());
        assertThat(reload(a).getInventoryUnitId()).isEqualTo(101L);

        doThrow(new InventoryUnavailableException("caido")).when(inventoryPort).release(anyLong(), anyLong());
        itemUseCase.changeStatus(a.getOrderId(), a.getId(), OrderItemStatus.CANCELLED, null, null);
        assertThat(reload(a).isInventoryReleasePending()).isTrue();
        InventorySyncTask arrival = arrivalTask(p, 2, List.of());
        clearInvocations(inventoryPort);

        executor.runForPurchase(p.getId());

        verify(inventoryPort).release(101L, a.getId());
        verify(inventoryPort, never()).registerPurchaseArrival(anyString(), anyLong(), anyInt(), anyList());
        assertThat(stored(arrival).getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
        assertThat(stored(arrival).getAttempts()).isZero();

        // la liberacion vuelve a funcionar: el job libera primero y despues envia la llegada
        clearInvocations(inventoryPort);
        org.mockito.Mockito.doNothing().when(inventoryPort).release(anyLong(), anyLong());
        job.run();

        var order = inOrder(inventoryPort);
        order.verify(inventoryPort).release(101L, a.getId());
        order.verify(inventoryPort).registerPurchaseArrival(anyString(), eq(p.getId()), eq(2), eq(List.of()));
        assertThat(reload(a).isInventoryReleasePending()).isFalse();
        assertThat(stored(arrival).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
    }

    /** Si la liberacion en linea tras CREATE_ENTRY falla, las tareas siguientes de la compra esperan. */
    @Test
    void inlineReleaseFails_laterTasksWait() {
        Purchase p = purchase(1);
        OrderItem a = coveredItems(p, 1).get(0);
        InventorySyncTask entry = createEntryTask(p, List.of(a.getId()));
        InventorySyncTask arrival = arrivalTask(p, 1, List.of());
        itemUseCase.changeStatus(a.getOrderId(), a.getId(), OrderItemStatus.CANCELLED, null, null);
        when(inventoryPort.createPurchaseEntry(anyString(), any()))
                .thenReturn(new PurchaseEntryResult(9L, Map.of(a.getId(), 101L)));
        doThrow(new InventoryUnavailableException("caido")).when(inventoryPort).release(anyLong(), anyLong());

        executor.runForPurchase(p.getId());

        assertThat(stored(entry).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
        assertThat(stored(arrival).getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
        verify(inventoryPort, never()).registerPurchaseArrival(anyString(), anyLong(), anyInt(), anyList());
    }

    /** Esperas entre colas (unidades aun no liberadas / reservadas por otra compra) son transitorias. */
    @Test
    void notEnoughUnitsOrAllocationConflict_transient_thenCompletes() {
        Purchase p = purchase(2);
        InventorySyncTask entry = createEntryTask(p, List.of());
        InventorySyncTask arrival = arrivalTask(p, 2, List.of());
        when(inventoryPort.createPurchaseEntry(anyString(), any()))
                .thenThrow(new InventorySyncRejectedException("ALLOCATION_CONFLICT", "ya cubierto"));

        executor.runForPurchase(p.getId());

        assertThat(stored(entry).getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
        assertThat(stored(entry).getAttempts()).isEqualTo(1);
        assertThat(stored(entry).getLastError()).contains("ALLOCATION_CONFLICT");
        verify(inventoryPort, never()).registerPurchaseArrival(anyString(), anyLong(), anyInt(), anyList());

        doReturn(new PurchaseEntryResult(9L, Map.of())).when(inventoryPort).createPurchaseEntry(anyString(), any());
        doThrow(new InventorySyncRejectedException("NOT_ENOUGH_PURCHASE_UNITS", "faltan"))
                .when(inventoryPort).registerPurchaseArrival(anyString(), anyLong(), anyInt(), anyList());
        executor.runForPurchase(p.getId());

        assertThat(stored(entry).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
        assertThat(stored(arrival).getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
        assertThat(stored(arrival).getAttempts()).isEqualTo(1);
        assertThat(stored(arrival).getLastError()).contains("NOT_ENOUGH_PURCHASE_UNITS");

        org.mockito.Mockito.doNothing().when(inventoryPort)
                .registerPurchaseArrival(anyString(), anyLong(), anyInt(), anyList());
        executor.runForPurchase(p.getId());

        assertThat(stored(arrival).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
    }

    @Test
    void transientConflict_afterMaxAttempts_failed() {
        Purchase p = purchase(1);
        InventorySyncTask entry = createEntryTask(p, List.of());
        when(inventoryPort.createPurchaseEntry(anyString(), any()))
                .thenThrow(new InventorySyncRejectedException("NOT_ENOUGH_PURCHASE_UNITS", "faltan"));

        for (int i = 1; i < InventorySyncExecutor.MAX_CONFLICT_ATTEMPTS; i++) {
            executor.runForPurchase(p.getId());
            assertThat(stored(entry).getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
            assertThat(stored(entry).getAttempts()).isEqualTo(i);
        }
        executor.runForPurchase(p.getId());

        InventorySyncTask failed = stored(entry);
        assertThat(failed.getStatus()).isEqualTo(InventorySyncTaskStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(InventorySyncExecutor.MAX_CONFLICT_ATTEMPTS);
        assertThat(failed.getLastError()).contains("NOT_ENOUGH_PURCHASE_UNITS");
    }

    /** Un fallo al aplicar el resultado localmente queda registrado como intento (sigue PENDING). */
    @Test
    void applyPhaseFailure_recordsAttempt() {
        long missingPurchase = 987_654L;
        InventorySyncTask t = task(missingPurchase, InventorySyncTaskType.CREATE_ENTRY,
                new CreatePurchaseEntry(missingPurchase, 7L, "SKU-1", "NEW", 1, new BigDecimal("1.00"), "USD",
                        LocalDate.of(2026, 11, 15), List.of()), "purchase-" + missingPurchase + "-entry");
        when(inventoryPort.createPurchaseEntry(anyString(), any())).thenReturn(new PurchaseEntryResult(9L, Map.of()));

        executor.runForPurchase(missingPurchase);

        InventorySyncTask stored = stored(t);
        assertThat(stored.getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
        assertThat(stored.getAttempts()).isEqualTo(1);
        assertThat(stored.getLastError()).isNotBlank();
    }

    @Test
    void doneTaskNotReRun() {
        Purchase p = purchase(2);
        InventorySyncTask entry = createEntryTask(p, List.of());
        when(inventoryPort.createPurchaseEntry(anyString(), any())).thenReturn(new PurchaseEntryResult(9L, Map.of()));
        executor.runForPurchase(p.getId());
        assertThat(stored(entry).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
        InventorySyncTask notReceived = task(p.getId(), InventorySyncTaskType.REGISTER_NOT_RECEIVED,
                new InventorySyncExecutor.NotReceivedPayload(1, List.of(), "perdido"),
                "purchase-" + p.getId() + "-not-received-1");
        clearInvocations(inventoryPort);

        executor.runForPurchase(p.getId());

        verify(inventoryPort, never()).createPurchaseEntry(anyString(), any());
        verify(inventoryPort).registerPurchaseNotReceived(
                "purchase-" + p.getId() + "-not-received-1", p.getId(), 1, List.of(), "perdido");
        assertThat(stored(notReceived).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);

        clearInvocations(inventoryPort);
        executor.runAllPending();
        executor.runForPurchase(p.getId());
        verifyNoMoreInteractions(inventoryPort);
    }

    @Test
    void insideTransaction_throws() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> executor.runForPurchase(1L)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> executor.runAllPending()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> executor.retryFailed(1L)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void job_isScheduledWithConfiguredDelay() throws Exception {
        Scheduled scheduled = InventorySyncJob.class.getDeclaredMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${orders.inventory-sync-job.delay-ms}");
    }
}
