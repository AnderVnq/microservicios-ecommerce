package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.domain.exception.QuotaExceededException;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.OrderItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dos operaciones que consumen el ultimo cupo del mismo documento en paralelo: solo una puede ganar.
 * Sin {@code @Transactional}: cada hilo confirma su propia transaccion.
 */
class QuotaConcurrencyTest extends ServiceTestSupport {

    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownPool() {
        pool.shutdownNow();
    }

    /** Documento nuevo por repeticion: las filas de lock sobreviven entre tests. */
    private static ImporterDocument freshDoc() {
        return dni("C" + UUID.randomUUID().toString().substring(0, 12));
    }

    @RepeatedTest(5)
    void twoParallelAssignments_lastSlot_onlyOneWins() throws Exception {
        ImporterDocument doc = freshDoc();
        seedUsedUnits(doc, ImporterType.HOLDER, ORDER_DATE, 2);
        List<OrderItem> pending = seedUnassignedUnits(1L, ORDER_DATE, 2);
        Long orderId = pending.get(0).getOrderId();
        stubFamilyMember(1L, 7L, doc);
        AssignImporterCommand cmd = new AssignImporterCommand(ImporterType.FAMILY_MEMBER, 7L);

        List<Outcome> outcomes = runTogether(
                () -> itemUseCase.assignImporter(orderId, pending.get(0).getId(), cmd),
                () -> itemUseCase.assignImporter(orderId, pending.get(1).getId(), cmd));

        assertThat(itemRepository.countQuotaUnits(doc, YEAR, null)).isEqualTo(3);
        assertThat(outcomes).filteredOn(Outcome::quotaExceeded).hasSize(1);
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
    }

    @RepeatedTest(5)
    void createAndAssignInParallel_lastSlot_onlyOneWins() throws Exception {
        ImporterDocument doc = freshDoc();
        seedUsedUnits(doc, ImporterType.HOLDER, ORDER_DATE, 2);
        // titular 1 tiene el documento; el familiar 7 del cliente 2 es la misma persona
        stubHolder(1L, doc);
        List<OrderItem> pending = seedUnassignedUnits(2L, ORDER_DATE, 1);
        stubFamilyMember(2L, 7L, doc);
        OrderItem otherClientUnit = pending.get(0);

        List<Outcome> outcomes = runTogether(
                () -> orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(1, "10.00"))),
                () -> itemUseCase.assignImporter(otherClientUnit.getOrderId(), otherClientUnit.getId(),
                        new AssignImporterCommand(ImporterType.FAMILY_MEMBER, 7L)));

        assertThat(itemRepository.countQuotaUnits(doc, YEAR, null)).isEqualTo(3);
        Outcome create = outcomes.get(0);
        Outcome assign = outcomes.get(1);
        assertThat(create.succeeded()).as("create nunca falla por cupo: deja la unidad UNASSIGNED").isTrue();
        long createdHolderUnits = ((OrderView) create.result()).items().stream()
                .filter(i -> i.getImporterType() == ImporterType.HOLDER).count();
        boolean assignLost = assign.quotaExceeded();
        boolean createLost = createdHolderUnits == 0;
        assertThat(assignLost ^ createLost).as("exactamente uno pierde (assignLost=%s, createLost=%s)",
                assignLost, createLost).isTrue();
    }

    private List<Outcome> runTogether(Callable<?> first, Callable<?> second) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = List.of(pool.submit(gated(startGate, first)), pool.submit(gated(startGate, second)));
        startGate.countDown();
        return futures.stream().map(QuotaConcurrencyTest::outcomeOf).toList();
    }

    private static Callable<Object> gated(CountDownLatch gate, Callable<?> action) {
        return () -> {
            gate.await();
            return action.call();
        };
    }

    private static Outcome outcomeOf(Future<?> future) {
        try {
            return new Outcome(future.get(30, TimeUnit.SECONDS), null);
        } catch (ExecutionException e) {
            return new Outcome(null, e.getCause());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record Outcome(Object result, Throwable error) {
        boolean succeeded() {
            return error == null;
        }

        boolean quotaExceeded() {
            return error instanceof QuotaExceededException;
        }
    }
}
