package com.example.mspedidos.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reintenta periodicamente las tareas de sincronizacion con inventario que siguen PENDING (spec 6.6). */
@Component
@RequiredArgsConstructor
class InventorySyncJob {

    private final InventorySyncExecutor executor;

    /** La primera pasada espera el mismo intervalo: no compite con el arranque. */
    @Scheduled(fixedDelayString = "${orders.inventory-sync-job.delay-ms}",
            initialDelayString = "${orders.inventory-sync-job.delay-ms}")
    void run() {
        executor.runAllPending();
    }
}
