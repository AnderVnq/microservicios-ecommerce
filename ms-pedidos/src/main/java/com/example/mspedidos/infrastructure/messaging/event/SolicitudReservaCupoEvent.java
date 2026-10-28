package com.example.mspedidos.infrastructure.messaging.event;

/**
 * Publicado por ms-pedidos cuando un pedido requiere importacion.
 * ms-clientes lo consume e intenta reservar una unidad del cupo del titular.
 */
public record SolicitudReservaCupoEvent(Long pedidoId, Long clienteId, String idempotencyKey) {
}
