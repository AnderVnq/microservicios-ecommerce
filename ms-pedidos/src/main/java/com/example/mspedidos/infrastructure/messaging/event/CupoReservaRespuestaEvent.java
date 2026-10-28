package com.example.mspedidos.infrastructure.messaging.event;

/**
 * Publicado por ms-clientes en respuesta a una SolicitudReservaCupoEvent:
 * exito=true si logro reservar la unidad de cupo, false si el titular ya
 * agoto sus importaciones (motivo tiene el detalle).
 */
public record CupoReservaRespuestaEvent(Long pedidoId, Long clienteId, String idempotencyKey,
                                         boolean exito, String motivo) {
}
