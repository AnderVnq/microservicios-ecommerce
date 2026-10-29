package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Doble envio de la misma llegada (misma clave, mismo cuerpo, toda la cantidad pendiente) sin {@code @Transactional}:
 * ambas peticiones pasan la verificacion previa y esperan el lock de la compra; la segunda debe ver la llegada de la
 * primera bajo el lock y responder como repeticion, no como cantidad excedida.
 */
class PurchaseArrivalConcurrencyTest extends PurchaseFlowTestSupport {

    @Autowired
    PlatformTransactionManager transactionManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownPool() {
        pool.shutdownNow();
    }

    @RepeatedTest(3)
    void sameKeySameHash_fullPending_concurrent_oneReplayed() throws Exception {
        OrderItem a = processing(order(day(5)));
        OrderItem b = processing(order(day(6)));
        Long id = buy(2, a, b);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        // el test retiene el lock de la compra para que ambas peticiones pasen la verificacion previa y se encolen
        Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            purchaseRepository.findByIdForUpdate(id).orElseThrow();
            lockHeld.countDown();
            try {
                releaseLock.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<PurchaseView> first = callers.submit(gated(startGate,
                    () -> purchases.registerArrival(id, "arr-dup", "h-dup", 2)));
            Future<PurchaseView> second = callers.submit(gated(startGate,
                    () -> purchases.registerArrival(id, "arr-dup", "h-dup", 2)));
            startGate.countDown();
            Thread.sleep(300);
            releaseLock.countDown();
            holder.get(10, TimeUnit.SECONDS);

            PurchaseView v1 = first.get(30, TimeUnit.SECONDS);
            PurchaseView v2 = second.get(30, TimeUnit.SECONDS);

            assertThat(v1.replayed() ^ v2.replayed()).isTrue();
        } finally {
            callers.shutdownNow();
        }
        assertThat(purchase(id).getReceivedQuantity()).isEqualTo(2);
        assertThat(purchaseRepository.findArrivals(id)).hasSize(1);
        assertThat(tasks(id, InventorySyncTaskType.REGISTER_ARRIVAL)).hasSize(1);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
    }

    private static <T> Callable<T> gated(CountDownLatch gate, Callable<T> action) {
        return () -> {
            gate.await();
            return action.call();
        };
    }
}
