package com.example.msinventory.application.service;

import com.example.msinventory.ProductsStubbedTest;
import com.example.msinventory.application.port.in.EntryResult;
import com.example.msinventory.domain.exception.ProductsUnavailableException;
import com.example.msinventory.domain.exception.UnknownSkuException;
import com.example.msinventory.application.port.in.InventoryEntryUseCase;
import com.example.msinventory.application.port.in.RegisterEntryCommand;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.application.port.out.InventoryEntryRepositoryPort;
import com.example.msinventory.application.port.out.InventoryMovementRepositoryPort;
import com.example.msinventory.domain.exception.ConditionNotFoundException;
import com.example.msinventory.domain.exception.IdempotencyKeyReusedException;
import com.example.msinventory.domain.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SpringBootTest
class InventoryEntryServiceTest extends ProductsStubbedTest {

    @Autowired InventoryEntryUseCase service;
    @Autowired InventoryMovementRepositoryPort movements;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ActorProvider actorProvider;
    @MockitoSpyBean InventoryEntryRepositoryPort entryPort;
    @MockitoSpyBean MovementRecorder recorder;

    @BeforeEach
    void setUp() {
        when(actorProvider.currentActor()).thenReturn("op-1");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from inventory_movements");
        jdbc.update("delete from inventory_units");
        jdbc.update("delete from inventory_entries");
    }

    private RegisterEntryCommand purchase(int qty, InventoryUnitStatus status, LocalDate eta, String invoice) {
        return new RegisterEntryCommand(EntryType.STOCK_PURCHASE, "SKU-1", "NEW", Warehouse.USA, qty,
                new BigDecimal("12.50"), null, status, eta, null, invoice, "AMZ-1", "src-ref", null);
    }

    private RegisterEntryCommand inTransit(int qty) {
        return purchase(qty, InventoryUnitStatus.IN_TRANSIT, LocalDate.now().plusDays(5), null);
    }

    @Test
    void entry_unknownSku_422_nothingWritten() {
        when(products.canonicalSku("SKU-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register("key-x", "h", inTransit(2)))
                .isInstanceOf(UnknownSkuException.class)
                .hasMessage("El SKU no existe en el catalogo");

        assertThat(count("inventory_entries")).isZero();
        assertThat(count("inventory_units")).isZero();
        assertThat(count("inventory_movements")).isZero();
    }

    @Test
    void entry_productsDown_503() {
        when(products.canonicalSku("SKU-1")).thenThrow(new ProductsUnavailableException("caido"));

        assertThatThrownBy(() -> service.register("key-y", "h", inTransit(2)))
                .isInstanceOf(ProductsUnavailableException.class);
        assertThat(count("inventory_entries")).isZero();
    }

    @Test
    void entry_typedSkuDifferentCase_storesCatalogSpelling() {
        // catalogo: ABC-1; el operador teclea " abc-1 " -> se valida recortado y las unidades quedan como ABC-1
        when(products.canonicalSku("abc-1")).thenReturn(Optional.of("ABC-1"));

        EntryResult r = service.register("key-case", "h", inTransit(2).withSku(" abc-1 "));

        assertThat(r.units()).hasSize(2).allSatisfy(u -> assertThat(u.getSku()).isEqualTo("ABC-1"));
        assertThat(jdbc.queryForList("select sku from inventory_units", String.class))
                .containsOnly("ABC-1");
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    @Test
    void register_createsNUnitsAndNMovements() {
        EntryResult r = service.register("key-1", "h1", inTransit(3));

        assertThat(r.replayed()).isFalse();
        assertThat(r.entry().getQuantity()).isEqualTo(3);
        assertThat(r.entry().getPerformedBy()).isEqualTo("op-1");
        assertThat(r.units()).hasSize(3)
                .allSatisfy(u -> {
                    assertThat(u.getId()).isNotNull();
                    assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.IN_TRANSIT);
                    assertThat(u.getUnitCost()).isEqualByComparingTo("12.50");
                    assertThat(u.getCostCurrency()).isEqualTo("USD");
                    assertThat(u.getEntryId()).isEqualTo(r.entry().getId());
                });
        assertThat(count("inventory_units")).isEqualTo(3);
        assertThat(count("inventory_movements")).isEqualTo(3);
        for (InventoryUnit u : r.units()) {
            List<InventoryMovement> ms = movements.findByUnitId(u.getId());
            assertThat(ms).hasSize(1);
            InventoryMovement m = ms.get(0);
            assertThat(m.type()).isEqualTo(MovementType.ENTRY_STOCK_PURCHASE);
            assertThat(m.sku()).isEqualTo("SKU-1");
            assertThat(m.conditionCode()).isEqualTo("NEW");
            assertThat(m.unitCost()).isEqualByComparingTo("12.50");
            assertThat(m.costCurrency()).isEqualTo("USD");
            assertThat(m.fromWarehouse()).isNull();
            assertThat(m.toWarehouse()).isEqualTo(Warehouse.USA);
            assertThat(m.reference()).isEqualTo("src-ref");
            assertThat(m.performedBy()).isEqualTo("op-1");
        }
    }

    @Test
    void register_referenceIsInvoiceWhenPresent_andCurrencyIsKept() {
        RegisterEntryCommand cmd = new RegisterEntryCommand(EntryType.STOCK_PURCHASE, "SKU-1", "new", Warehouse.LIMA, 1,
                new BigDecimal("5.00"), "PEN", InventoryUnitStatus.AVAILABLE, null, null, "F-1", null, "src", null);
        EntryResult r = service.register("key-inv", "h", cmd);

        InventoryUnit u = r.units().get(0);
        assertThat(u.getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(u.getArrivedAt()).isNotNull();
        assertThat(u.getCostCurrency()).isEqualTo("PEN");
        assertThat(movements.findByUnitId(u.getId()).get(0).reference()).isEqualTo("F-1");
    }

    @Test
    void sameKeySameBody_returnsSameEntry() {
        EntryResult first = service.register("key-1", "h1", inTransit(3));
        EntryResult second = service.register("key-1", "h1", inTransit(3));

        assertThat(second.replayed()).isTrue();
        assertThat(second.entry().getId()).isEqualTo(first.entry().getId());
        assertThat(second.units()).extracting(InventoryUnit::getId)
                .containsExactlyInAnyOrderElementsOf(first.units().stream().map(InventoryUnit::getId).toList());
        assertThat(count("inventory_units")).isEqualTo(3);
        assertThat(count("inventory_movements")).isEqualTo(3);
        assertThat(count("inventory_entries")).isEqualTo(1);
    }

    @Test
    void sameKeyOtherBody_rejected() {
        service.register("key-1", "h1", inTransit(3));

        assertThatThrownBy(() -> service.register("key-1", "h-other", inTransit(5)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(count("inventory_units")).isEqualTo(3);
    }

    @Test
    void inTransitWithoutArrivalDate_rejected() {
        assertThatThrownBy(() -> service.register("k", "h", purchase(1, InventoryUnitStatus.IN_TRANSIT, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("inventory_entries")).isZero();
    }

    @Test
    void adjustmentWithoutReason_rejected() {
        RegisterEntryCommand cmd = new RegisterEntryCommand(EntryType.ADJUSTMENT_IN, "SKU-1", "NEW", Warehouse.USA, 1,
                BigDecimal.TEN, null, InventoryUnitStatus.AVAILABLE, null, null, null, null, null, "  ");
        assertThatThrownBy(() -> service.register("k", "h", cmd)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("inventory_entries")).isZero();
    }

    @Test
    void adjustmentWithReason_usesAdjustmentMovementWithReason() {
        RegisterEntryCommand cmd = new RegisterEntryCommand(EntryType.ADJUSTMENT_IN, "SKU-1", "NEW", Warehouse.USA, 1,
                BigDecimal.TEN, null, InventoryUnitStatus.AVAILABLE, null, null, null, null, null, "conteo fisico");
        EntryResult r = service.register("k", "h", cmd);
        InventoryMovement m = movements.findByUnitId(r.units().get(0).getId()).get(0);
        assertThat(m.type()).isEqualTo(MovementType.ENTRY_ADJUSTMENT);
        assertThat(m.reason()).isEqualTo("conteo fisico");
    }

    @Test
    void invalidQuantityOrStatus_rejected() {
        assertThatThrownBy(() -> service.register("k", "h", inTransit(0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register("k2", "h",
                purchase(1, InventoryUnitStatus.ALLOCATED, null, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inactiveCondition_rejected() {
        jdbc.update("update item_conditions set active = false where code = 'NEW'");
        try {
            assertThatThrownBy(() -> service.register("k", "h", inTransit(1)))
                    .isInstanceOf(com.example.msinventory.domain.exception.ConditionInactiveException.class);
            assertThat(count("inventory_entries")).isZero();
        } finally {
            jdbc.update("update item_conditions set active = true where code = 'NEW'");
        }
    }

    @Test
    void unknownCondition_notFound() {
        RegisterEntryCommand cmd = new RegisterEntryCommand(EntryType.STOCK_PURCHASE, "SKU-1", "NOPE", Warehouse.USA, 1,
                BigDecimal.TEN, null, InventoryUnitStatus.AVAILABLE, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.register("k", "h", cmd)).isInstanceOf(ConditionNotFoundException.class);
    }

    @Test
    void repeatedInvoice_warnsButRegisters() {
        EntryResult first = service.register("k1", "h1",
                purchase(1, InventoryUnitStatus.AVAILABLE, null, "F-100"));
        assertThat(first.warnings()).isEmpty();

        EntryResult second = service.register("k2", "h2",
                purchase(2, InventoryUnitStatus.AVAILABLE, null, "F-100"));

        assertThat(second.warnings()).containsExactly("Factura F-100 ya registrada");
        assertThat(second.units()).hasSize(2);
        assertThat(count("inventory_units")).isEqualTo(3);
    }

    @Test
    void failureAfterSavingUnits_rollsBackEverything_andKeyCanBeRetried() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(inv -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("fallo simulado");
            }
            return inv.callRealMethod();
        }).when(recorder).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.register("atomic", "h", inTransit(3)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("inventory_entries")).isZero();
        assertThat(count("inventory_units")).isZero();
        assertThat(count("inventory_movements")).isZero();

        EntryResult retry = service.register("atomic", "h", inTransit(3));
        assertThat(retry.replayed()).isFalse();
        assertThat(retry.units()).hasSize(3);
        assertThat(count("inventory_entries")).isEqualTo(1);
        assertThat(count("inventory_units")).isEqualTo(3);
        assertThat(count("inventory_movements")).isEqualTo(3);
    }

    @Test
    void concurrentSameNewKey_loserReplaysWinner() throws Exception {
        // Fuerza la carrera de forma determinista: ambos hilos pasan la lectura inicial "no existe".
        CountDownLatch bothRead = new CountDownLatch(2);
        org.mockito.Mockito.doAnswer(inv -> {
            Object result = inv.callRealMethod();
            if (((Optional<?>) result).isEmpty()) {
                bothRead.countDown();
                bothRead.await();
            }
            return result;
        }).when(entryPort).findByIdempotencyKey(anyString());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<EntryResult> a = pool.submit(() -> service.register("race", "h", inTransit(2)));
            Future<EntryResult> b = pool.submit(() -> service.register("race", "h", inTransit(2)));
            EntryResult ra = a.get(10, java.util.concurrent.TimeUnit.SECONDS);
            EntryResult rb = b.get(10, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(List.of(ra.replayed(), rb.replayed())).containsExactlyInAnyOrder(true, false);
            assertThat(ra.entry().getId()).isEqualTo(rb.entry().getId());
            assertThat(ra.units()).hasSize(2);
            assertThat(rb.units()).hasSize(2);
            assertThat(count("inventory_entries")).isEqualTo(1);
            assertThat(count("inventory_units")).isEqualTo(2);
            assertThat(count("inventory_movements")).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentSameNewKeyOtherBody_loserRejected() throws Exception {
        CountDownLatch bothRead = new CountDownLatch(2);
        org.mockito.Mockito.doAnswer(inv -> {
            Object result = inv.callRealMethod();
            if (((Optional<?>) result).isEmpty()) {
                bothRead.countDown();
                bothRead.await();
            }
            return result;
        }).when(entryPort).findByIdempotencyKey(anyString());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<EntryResult> a = pool.submit(() -> service.register("race2", "hA", inTransit(1)));
            Future<EntryResult> b = pool.submit(() -> service.register("race2", "hB", inTransit(1)));
            int ok = 0, reused = 0;
            for (Future<EntryResult> f : List.of(a, b)) {
                try {
                    f.get(10, java.util.concurrent.TimeUnit.SECONDS);
                    ok++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(IdempotencyKeyReusedException.class);
                    reused++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(reused).isEqualTo(1);
            assertThat(count("inventory_entries")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
