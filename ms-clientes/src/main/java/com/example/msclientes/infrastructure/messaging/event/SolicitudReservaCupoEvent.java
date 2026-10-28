package com.example.msclientes.infrastructure.messaging.event;

/**
 * Publicado por ms-pedidos cuando un pedido requiere importacion.
 * Mismo contrato que su contraparte en ms-pedidos (ver nota en RabbitMQConfig).
 */
public record SolicitudReservaCupoEvent(Long pedidoId, Long clienteId, String idempotencyKey) {
}
