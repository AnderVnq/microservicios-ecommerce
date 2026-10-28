package com.example.msclientes.infrastructure.messaging.event;

/** Accion compensatoria del saga: libera el cupo de un pedido confirmado que se cancela. */
public record PedidoCanceladoEvent(Long pedidoId, Long clienteId, String idempotencyKey) {
}
