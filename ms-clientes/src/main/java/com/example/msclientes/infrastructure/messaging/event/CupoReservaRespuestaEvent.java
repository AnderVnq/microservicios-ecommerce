package com.example.msclientes.infrastructure.messaging.event;

/** Publicado por ms-clientes en respuesta a una SolicitudReservaCupoEvent. */
public record CupoReservaRespuestaEvent(Long pedidoId, Long clienteId, String idempotencyKey,
                                         boolean exito, String motivo) {
}
