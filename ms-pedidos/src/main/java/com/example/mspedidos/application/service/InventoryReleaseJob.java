package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.InventoryAllocationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reintenta periodicamente las liberaciones de inventario que quedaron pendientes (spec 10.2). */
@Component
@RequiredArgsConstructor
class InventoryReleaseJob {

    private final InventoryAllocationUseCase allocation;

    /** La primera pasada espera el mismo intervalo: no compite con el arranque. */
    @Scheduled(fixedDelayString = "${orders.inventory-release-job.delay-ms}",
            initialDelayString = "${orders.inventory-release-job.delay-ms}")
    void retryPendingReleases() {
        allocation.retryPendingReleases();
    }
}
