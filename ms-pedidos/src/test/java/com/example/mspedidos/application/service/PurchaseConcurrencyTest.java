package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.PurchaseUseCase;
import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.application.port.in.RegisterPurchaseCommand;
import com.example.mspedidos.application.port.out.InventoryPort.PurchaseEntryResult;
import com.example.mspedidos.application.port.out.SupplierRepositoryPort;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Dos compras simultaneas del mismo SKU (sin {@code @Transactional}: cada una confirma de verdad) nunca cubren la
 * misma unidad de pedido; la que pierde unidades completa con las que quedan libres y el resto es sobrante.
 */
class PurchaseConcurrencyTest extends ServiceTestSupport {

    @Autowired
    PurchaseUseCase useCase;
    @Autowired
    SupplierRepositoryPort supplierRepository;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @BeforeEach
    void cleanPurchases() {
        jdbc.update("delete from inventory_sync_tasks");
        jdbc.update("delete from purchase_arrivals");
        jdbc.update("delete from purchase_non_receipts");
        jdbc.update("delete from purchases");
        when(inventoryPort.isConditionActive("NEW")).thenReturn(true);
        when(inventoryPort.createPurchaseEntry(anyString(), any())).thenReturn(new PurchaseEntryResult(1L, Map.of()));
    }

    @AfterEach
    void shutdownPool() {
        pool.shutdownNow();
    }

    @RepeatedTest(5)
    void concurrentPurchasesSameSku_noUnitCoveredTwice() throws Exception {
        String sku = "SKU-C-" + UUID.randomUUID();
        Supplier supplier = supplierRepository.save(Supplier.create("Prov " + uniqueExternalId(), "US", null, null));
        Order order = seedOrder(1L, ORDER_DATE);
        List<OrderItem> units = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            units.add(itemRepository.save(OrderItem.builder().orderId(order.getId()).sku(sku).externalItemId("E")
                    .unitPrice(BigDecimal.TEN).status(OrderItemStatus.PROCESSING)
                    .importerType(OrderItem.newUnit(sku, "E", BigDecimal.TEN).getImporterType()).build()));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        Future<PurchaseView> first = pool.submit(gated(startGate,
                () -> useCase.register("k-a-" + sku, "h-a", command(supplier, sku))));
        Future<PurchaseView> second = pool.submit(gated(startGate,
                () -> useCase.register("k-b-" + sku, "h-b", command(supplier, sku))));
        startGate.countDown();
        PurchaseView a = first.get(30, TimeUnit.SECONDS);
        PurchaseView b = second.get(30, TimeUnit.SECONDS);

        assertThat(a.coveredItems().size() + b.coveredItems().size()).isEqualTo(5);
        assertThat(a.pendingSurplus() + b.pendingSurplus()).isEqualTo(3);
        List<Long> coveredIds = new ArrayList<>();
        a.coveredItems().forEach(i -> coveredIds.add(i.getId()));
        b.coveredItems().forEach(i -> coveredIds.add(i.getId()));
        assertThat(coveredIds).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(units.stream().map(OrderItem::getId).toList());
        for (OrderItem unit : units) {
            OrderItem stored = itemRepository.findById(unit.getId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
            assertThat(stored.getPurchaseId()).isIn(a.purchase().getId(), b.purchase().getId());
            assertThat(historyCount(unit.getId(), HistoryEventType.PURCHASE_COVERED)).isEqualTo(1);
        }
    }

    private static RegisterPurchaseCommand command(Supplier supplier, String sku) {
        return new RegisterPurchaseCommand(supplier.getId(), sku, "NEW", 4, new BigDecimal("12.50"), "USD", "SUP-1",
                null, LocalDate.of(2026, 11, 15), null);
    }

    private static <T> Callable<T> gated(CountDownLatch gate, Callable<T> action) {
        return () -> {
            gate.await();
            return action.call();
        };
    }
}
