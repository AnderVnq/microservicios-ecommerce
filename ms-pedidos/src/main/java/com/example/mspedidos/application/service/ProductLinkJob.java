package com.example.mspedidos.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Enlaza periodicamente las unidades pendientes de vinculo con el catalogo (spec 8.3). */
@Component
@RequiredArgsConstructor
class ProductLinkJob {

    private final ProductLinker linker;

    /** La primera pasada espera el mismo intervalo: no compite con el arranque. */
    @Scheduled(fixedDelayString = "${orders.product-link-job.delay-ms}",
            initialDelayString = "${orders.product-link-job.delay-ms}")
    void run() {
        linker.linkPending();
    }
}
