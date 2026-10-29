package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.AllocationUseCase;
import com.example.msinventory.application.port.in.InventoryEntryUseCase;
import com.example.msinventory.application.port.in.InventoryUnitUseCase;
import com.example.msinventory.application.port.in.RegisterEntryCommand;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort;
import com.example.msinventory.domain.exception.AllocationConflictException;
import com.example.msinventory.domain.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** No transaccional a proposito: cada hilo abre su propia transaccion. */
@SpringBootTest
class AllocationConcurrencyTest {

    @Autowired AllocationUseCase service;
    @Autowired InventoryEntryUseCase entryService;
    @Autowired InventoryUnitUseCase unitService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ActorProvider actorProvider;
    @MockitoSpyBean InventoryUnitRepositoryPort unitPort;

    @BeforeEach
    void setUp() {
        when(actorProvider.currentActor()).thenReturn("op-1");
    }

    @AfterEach
    void cleanup() {
        org.mockito.Mockito.reset(unitPort);
        jdbc.update("delete from inventory_movements");
        jdbc.update("delete from inventory_units");
        jdbc.update("delete from inventory_entries");
    }

    private Long available(String key) {
        RegisterEntryCommand cmd = new RegisterEntryCommand(EntryType.STOCK_PURCHASE, "SKU-1", "NEW", Warehouse.LIMA, 1,
                new BigDecimal("12.50"), null, InventoryUnitStatus.AVAILABLE, null, null, null, null, null, null);
        return entryService.register(key, "h", cmd).units().get(0).getId();
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    /**
     * Fuerza el entrelazado: tras leer la unidad, cada hilo espera a que el otro tambien haya leido.
     * Con bloqueo de fila el segundo hilo queda bloqueado en la lectura, el rendezvous expira (1 s)
     * y el ganador sigue; sin bloqueo ambos leen DISPONIBLE y ambos intentan asignar.
     */
    private void delayAfterUnitRead() {
        CountDownLatch bothRead = new CountDownLatch(2);
        Answer2 delay = inv -> {
            Object result = inv.callRealMethod();
            bothRead.countDown();
            bothRead.await(300, TimeUnit.MILLISECONDS);
            return result;
        };
        doAnswer(delay).when(unitPort).findByIdForUpdate(anyLong());
        doAnswer(delay).when(unitPort).findById(anyLong());
    }

    private interface Answer2 extends org.mockito.stubbing.Answer<Object> {
    }

    @RepeatedTest(5)
    void twoOrderItemsSameUnit_onlyOneWins() throws Exception {
        Long unit = available("k-" + System.nanoTime());
        delayAfterUnitRead();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<InventoryUnit> a = pool.submit(() -> service.allocate(unit, 1L, "SKU-1"));
            Future<InventoryUnit> b = pool.submit(() -> service.allocate(unit, 2L, "SKU-1"));
            int ok = 0, conflicts = 0;
            for (Future<InventoryUnit> f : List.of(a, b)) {
                try {
                    f.get(15, TimeUnit.SECONDS);
                    ok++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(AllocationConflictException.class);
                    conflicts++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(count("select count(*) from inventory_movements where movement_type = 'EXIT_ORDER_ALLOCATION'"))
                    .isEqualTo(1);
            assertThat(unitService.findOne(unit).getStatus()).isEqualTo(InventoryUnitStatus.ALLOCATED);
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(5)
    void oneOrderItemTwoUnits_onlyOneWins() throws Exception {
        Long u1 = available("k1-" + System.nanoTime());
        Long u2 = available("k2-" + System.nanoTime());
        // Ambos hilos comprueban "item sin cubrir" antes de que cualquiera guarde: solo la restriccion unica los separa.
        CountDownLatch bothChecked = new CountDownLatch(2);
        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            if (((Optional<?>) result).isEmpty()) {
                bothChecked.countDown();
                bothChecked.await(5, TimeUnit.SECONDS);
            }
            return result;
        }).when(unitPort).findByAllocatedOrderItemId(anyLong());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<InventoryUnit> a = pool.submit(() -> service.allocate(u1, 9L, "SKU-1"));
            Future<InventoryUnit> b = pool.submit(() -> service.allocate(u2, 9L, "SKU-1"));
            int ok = 0, conflicts = 0;
            Long winner = null;
            String conflictMessage = null;
            for (Future<InventoryUnit> f : List.of(a, b)) {
                try {
                    winner = f.get(15, TimeUnit.SECONDS).getId();
                    ok++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(AllocationConflictException.class);
                    conflictMessage = e.getCause().getMessage();
                    conflicts++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(conflictMessage).contains("cubierto por la unidad " + winner);
            assertThat(count("select count(*) from inventory_units where allocated_order_item_id = 9")).isEqualTo(1);
            assertThat(count("select count(*) from inventory_movements where movement_type = 'EXIT_ORDER_ALLOCATION'"))
                    .isEqualTo(1);
            assertThat(count("select count(*) from inventory_units where status = 'AVAILABLE'")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
