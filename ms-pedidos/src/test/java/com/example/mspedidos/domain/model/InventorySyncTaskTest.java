package com.example.mspedidos.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventorySyncTaskTest {

    private InventorySyncTask task(InventorySyncTaskStatus status) {
        return InventorySyncTask.builder().id(1L).purchaseId(2L).type(InventorySyncTaskType.CREATE_ENTRY)
                .payload("{}").idempotencyKey("k").status(status).build();
    }

    @Test
    void resetForRetry_failedBackToPending_keepsAttempts() {
        InventorySyncTask t = task(InventorySyncTaskStatus.PENDING);
        t.markFailed("NOT_FOUND: x");

        t.resetForRetry();

        assertThat(t.getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
        assertThat(t.getAttempts()).isEqualTo(1);
        assertThat(t.getLastError()).isEqualTo("NOT_FOUND: x");
    }

    @Test
    void resetForRetry_onlyFromFailed() {
        assertThatThrownBy(() -> task(InventorySyncTaskStatus.PENDING).resetForRetry())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> task(InventorySyncTaskStatus.DONE).resetForRetry())
                .isInstanceOf(IllegalStateException.class);
    }
}
