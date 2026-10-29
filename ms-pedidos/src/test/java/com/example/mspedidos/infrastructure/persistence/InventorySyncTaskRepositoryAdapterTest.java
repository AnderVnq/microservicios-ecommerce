package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(InventorySyncTaskRepositoryAdapter.class)
class InventorySyncTaskRepositoryAdapterTest {

    @Autowired
    InventorySyncTaskRepositoryPort tasks;

    private InventorySyncTask save(Long purchaseId, String key, InventorySyncTaskStatus status) {
        return tasks.save(InventorySyncTask.builder().purchaseId(purchaseId).type(InventorySyncTaskType.CREATE_ENTRY)
                .payload("{\"a\":1}").idempotencyKey(key).status(status).build());
    }

    @Test
    void syncTasks_pendingOrderedById() {
        InventorySyncTask t1 = save(1L, "k1", InventorySyncTaskStatus.PENDING);
        save(1L, "k2", InventorySyncTaskStatus.DONE);
        InventorySyncTask t3 = save(2L, "k3", InventorySyncTaskStatus.PENDING);
        save(2L, "k4", InventorySyncTaskStatus.FAILED);

        assertThat(tasks.findPendingOrdered()).extracting(InventorySyncTask::getId)
                .containsExactly(t1.getId(), t3.getId());
        assertThat(tasks.findByPurchaseId(2L)).hasSize(2);
    }

    @Test
    void syncTasks_updateAndForUpdate() {
        InventorySyncTask t = save(1L, "k1", InventorySyncTaskStatus.PENDING);
        t.recordAttempt("boom");
        tasks.save(t);

        InventorySyncTask found = tasks.findByIdForUpdate(t.getId()).orElseThrow();
        assertThat(found.getAttempts()).isEqualTo(1);
        assertThat(found.getLastError()).isEqualTo("boom");
        assertThat(found.getPayload()).isEqualTo("{\"a\":1}");
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
