package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.domain.exception.ImporterChangeNotAllowedException;
import com.example.mspedidos.domain.exception.QuotaExceededException;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Warehouse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Escritura obsoleta de una unidad: un cambio de estado que leyo la unidad antes de una reasignacion concurrente
 * no puede devolverle el importador anterior y dejar al documento por encima del cupo.
 * La intercalacion se fuerza con un spy que pausa el {@code save} del cambio de estado (H2 no la reproduce sola).
 */
class ItemRowLockConcurrencyTest extends ServiceTestSupport {

    @MockitoSpyBean
    OrderItemRepositoryPort spiedItems;
    @Autowired
    OrderItemUseCase itemUseCase;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownPool() {
        pool.shutdownNow();
    }

    @Test
    void changeStatusRacingReassign_neverLeavesDocumentAboveQuota() throws Exception {
        ImporterDocument docA = dni("A" + UUID.randomUUID().toString().substring(0, 12));
        ImporterDocument docB = dni("B" + UUID.randomUUID().toString().substring(0, 12));
        seedUsedUnits(docA, ImporterType.HOLDER, ORDER_DATE, 2);
        Order order = seedOrder(1L, ORDER_DATE);
        OrderItem u1 = itemRepository.save(OrderItem.builder().orderId(order.getId()).sku("SKU-1")
                .externalItemId("EXT-1").unitPrice(BigDecimal.TEN).status(OrderItemStatus.RECEIVED_AT_WAREHOUSE)
                .warehouse(Warehouse.values()[0]).importerType(ImporterType.HOLDER).importerDocument(docA).build());
        OrderItem u2 = itemRepository.save(OrderItem.builder().orderId(order.getId()).sku("SKU-2")
                .externalItemId("EXT-2").unitPrice(BigDecimal.TEN).status(OrderItemStatus.PROCESSING)
                .importerType(ImporterType.UNASSIGNED).build());
        stubHolder(1L, docA);
        stubFamilyMember(1L, 7L, docB);

        // T1 (cambio de estado) se pausa justo antes de escribir u1
        CountDownLatch t1Read = new CountDownLatch(1);
        CountDownLatch releaseT1 = new CountDownLatch(1);
        Thread[] t1Thread = new Thread[1];
        doAnswer(inv -> {
            if (Thread.currentThread() == t1Thread[0]) {
                t1Read.countDown();
                assertThat(releaseT1.await(30, TimeUnit.SECONDS)).isTrue();
            }
            return inv.callRealMethod();
        }).when(spiedItems).save(any());

        Future<?> t1 = pool.submit(() -> {
            t1Thread[0] = Thread.currentThread();
            return itemUseCase.changeStatus(order.getId(), u1.getId(), OrderItemStatus.READY_TO_SHIP, null, null);
        });
        assertThat(t1Read.await(30, TimeUnit.SECONDS)).isTrue();

        // T2: reasigna u1 al familiar B (libera el cupo de A si llega a confirmar antes que T1)
        Future<?> t2 = pool.submit(() -> itemUseCase.assignImporter(order.getId(), u1.getId(),
                new AssignImporterCommand(ImporterType.FAMILY_MEMBER, 7L)));
        try {
            t2.get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException blockedOnRowLock) {
            // con lock de fila T2 espera a que T1 confirme
        } catch (Exception ignored) {
            // el resultado de T2 se revisa al final
        }

        // T3: asigna otra unidad a A mientras T1 sigue abierto
        Throwable t3Error = catchThrowable(() -> itemUseCase.assignImporter(order.getId(), u2.getId(),
                new AssignImporterCommand(ImporterType.HOLDER, null)));

        releaseT1.countDown();
        t1.get(30, TimeUnit.SECONDS);
        Throwable t2Error = catchThrowable(() -> t2.get(30, TimeUnit.SECONDS));

        assertThat(itemRepository.countQuotaUnits(docA, YEAR, null))
                .as("A nunca supera el cupo (T3 error=%s)", t3Error).isLessThanOrEqualTo(3);
        // serializado: T3 ve u1 aun en A; T2 ve u1 ya en READY_TO_SHIP y no puede cambiar el importador
        assertThat(t3Error).isInstanceOf(QuotaExceededException.class);
        assertThat(t2Error).hasCauseInstanceOf(ImporterChangeNotAllowedException.class);
        OrderItem finalU1 = itemRepository.findByIdAndOrderId(u1.getId(), order.getId()).orElseThrow();
        assertThat(finalU1.getStatus()).isEqualTo(OrderItemStatus.READY_TO_SHIP);
        assertThat(finalU1.getImporterDocument()).isEqualTo(docA);
    }
}
