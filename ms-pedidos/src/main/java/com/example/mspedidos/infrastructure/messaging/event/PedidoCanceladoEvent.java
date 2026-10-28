package com.example.mspedidos.infrastructure.messaging.event;

/**
 * Accion compensatoria del saga: publicado cuando se cancela un pedido que
 * ya tenia el cupo de importacion reservado, para que ms-clientes lo libere.
 */
public record PedidoCanceladoEvent(Long pedidoId, Long clienteId, String idempotencyKey) {
}
