package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.PurchaseUseCase;
import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.application.port.in.RegisterPurchaseCommand;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.SupplierRepositoryPort;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Supplier;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * Seleccion automatica con unidades perdidas: cada intento bloquea con un solo {@code findByIdsForUpdate} en su
 * propia transaccion (nunca un segundo lock en la misma transaccion, que podria bloquear un id menor despues de uno
 * mayor); al perder unidades se reintenta desde cero y en el ultimo intento se usa lo que se tiene.
 * Las perdidas se simulan con un spy que, justo antes de bloquear, deja la unidad mas urgente en PURCHASED desde
 * otra conexion (como una compra concurrente que confirmo antes).
 */
class PurchaseSelectionRetryTest extends ServiceTestSupport {

    static final String SKU = "SKU-R";

    @MockitoSpyBean
    OrderItemRepositoryPort spiedItems;
    @Autowired
    PurchaseUseCase useCase;
    @Autowired
    SupplierRepositoryPort supplierRepository;
    @Autowired
    EntityManagerFactory emf;

    private final ExecutorService otherConnection = Executors.newSingleThreadExecutor();
    private Supplier supplier;

    @BeforeEach
    void cleanPurchases() {
        jdbc.update("delete from inventory_sync_tasks");
        jdbc.update("delete from purchase_arrivals");
        jdbc.update("delete from purchase_non_receipts");
        jdbc.update("delete from purchases");
        supplier = supplierRepository.save(Supplier.create("Prov " + uniqueExternalId(), "US", null, null));
        org.mockito.Mockito.when(inventoryPort.isConditionActive("NEW")).thenReturn(true);
        // sin aplicar la entrada: el executor no bloquea unidades y no ensucia el conteo de locks
        doThrow(new InventoryUnavailableException("caido")).when(inventoryPort)
                .createPurchaseEntry(anyString(), any());
    }

    @AfterEach
    void shutdown() {
        otherConnection.shutdownNow();
    }

    private List<OrderItem> fiveUnits() {
        Order order = seedOrder(1L, ORDER_DATE);
        List<OrderItem> units = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            units.add(itemRepository.save(OrderItem.builder().orderId(order.getId()).sku(SKU).externalItemId("E")
                    .unitPrice(BigDecimal.TEN).status(OrderItemStatus.PROCESSING)
                    .importerType(OrderItem.newUnit(SKU, SKU, "E", BigDecimal.TEN).getImporterType()).build()));
        }
        return units;
    }

    private RegisterPurchaseCommand command(int quantity) {
        return new RegisterPurchaseCommand(supplier.getId(), SKU, "NEW", quantity, BigDecimal.ONE, null, "SUP-1",
                null, LocalDate.of(2026, 11, 15), null);
    }

    private OrderItem reload(OrderItem item) {
        return itemRepository.findById(item.getId()).orElseThrow();
    }

    private void markPurchasedFromOtherConnection(Long itemId) throws Exception {
        otherConnection.submit(() -> jdbc.update("update order_items set status = 'PURCHASED' where id = ?", itemId))
                .get(10, TimeUnit.SECONDS);
    }

    @Test
    void unitsLostEveryAttempt_oneLockCallPerTransaction_lastAttemptKeepsWhatIsHeld() throws Exception {
        List<OrderItem> u = fiveUnits();
        List<List<Long>> lockCalls = new ArrayList<>();
        List<Object> lockTransactions = new ArrayList<>();
        doAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            lockCalls.add(List.copyOf(ids));
            lockTransactions.add(TransactionSynchronizationManager.getResource(emf));
            markPurchasedFromOtherConnection(ids.iterator().next());
            return inv.callRealMethod();
        }).when(spiedItems).findByIdsForUpdate(any());

        PurchaseView view = useCase.register("k-1", "h-1", command(2));

        // intento 1 pierde u0, intento 2 pierde u1, intento 3 (ultimo) pierde u2 y se queda con u3
        assertThat(lockCalls).containsExactly(
                List.of(u.get(0).getId(), u.get(1).getId()),
                List.of(u.get(1).getId(), u.get(2).getId()),
                List.of(u.get(2).getId(), u.get(3).getId()));
        assertThat(lockTransactions).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(view.coveredItems()).extracting(OrderItem::getId).containsExactly(u.get(3).getId());
        assertThat(view.pendingSurplus()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from purchases", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_sync_tasks", Long.class)).isEqualTo(1);
        assertThat(reload(u.get(4)).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        for (int i = 0; i < 3; i++) {
            assertThat(reload(u.get(i)).getPurchaseId()).as("unidad perdida %s", i).isNull();
        }
    }

    @Test
    void unitsLostOnce_retriedFromScratch_fullQuantityCovered() throws Exception {
        List<OrderItem> u = fiveUnits();
        AtomicBoolean firstCall = new AtomicBoolean(true);
        List<Object> lockTransactions = new ArrayList<>();
        doAnswer(inv -> {
            lockTransactions.add(TransactionSynchronizationManager.getResource(emf));
            if (firstCall.getAndSet(false)) {
                Collection<Long> ids = inv.getArgument(0);
                markPurchasedFromOtherConnection(ids.iterator().next());
            }
            return inv.callRealMethod();
        }).when(spiedItems).findByIdsForUpdate(any());

        PurchaseView view = useCase.register("k-1", "h-1", command(2));

        assertThat(lockTransactions).hasSize(2).doesNotHaveDuplicates();
        assertThat(view.coveredItems()).extracting(OrderItem::getId)
                .containsExactly(u.get(1).getId(), u.get(2).getId());
        assertThat(view.pendingSurplus()).isZero();
    }

    @Test
    void lockFailure_retriedFromScratch() {
        List<OrderItem> u = fiveUnits();
        AtomicBoolean firstCall = new AtomicBoolean(true);
        doAnswer(inv -> {
            if (firstCall.getAndSet(false)) {
                throw new CannotAcquireLockException("deadlock simulado");
            }
            return inv.callRealMethod();
        }).when(spiedItems).findByIdsForUpdate(any());

        PurchaseView view = useCase.register("k-1", "h-1", command(2));

        assertThat(view.coveredItems()).extracting(OrderItem::getId)
                .containsExactly(u.get(0).getId(), u.get(1).getId());
        assertThat(jdbc.queryForObject("select count(*) from purchases", Long.class)).isEqualTo(1);
    }
}
