package com.example.mspedidos.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Tarea pendiente de sincronizar con ms-inventory (reintentable). */
@Getter
@Builder
@AllArgsConstructor
public class InventorySyncTask {

    private Long id;
    private Long purchaseId;
    private InventorySyncTaskType type;
    private String payload;
    private String idempotencyKey;
    private InventorySyncTaskStatus status;
    private int attempts;
    private String lastError;
    private Instant createdAt;
    private Instant doneAt;

    public void markDone() {
        this.status = InventorySyncTaskStatus.DONE;
        this.doneAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = InventorySyncTaskStatus.FAILED;
        this.attempts++;
        this.lastError = error;
    }

    /** Un intento fallido que deja la tarea PENDING para reintentar. */
    public void recordAttempt(String error) {
        this.attempts++;
        this.lastError = error;
    }

    /** Reintento manual: una tarea FAILED vuelve a PENDING conservando intentos y ultimo error. */
    public void resetForRetry() {
        if (status != InventorySyncTaskStatus.FAILED) {
            throw new IllegalStateException("Solo una tarea FAILED puede reintentarse, actual: " + status);
        }
        this.status = InventorySyncTaskStatus.PENDING;
    }
}
