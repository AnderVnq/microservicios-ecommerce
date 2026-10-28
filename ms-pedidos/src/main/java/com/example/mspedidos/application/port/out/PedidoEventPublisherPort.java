package com.example.mspedidos.application.port.out;

/**
 * Puerto hacia el bus de eventos del saga. ms-pedidos nunca llama
 * sincronamente a ms-clientes: publica su intencion y sigue, dejando el
 * pedido en PENDIENTE hasta que llegue la respuesta del saga.
 */
public interface PedidoEventPublisherPort {

    void publicarSolicitudReservaCupo(Long pedidoId, Long clienteId, String idempotencyKey);

    /** Accion compensatoria: libera el cupo ya reservado de un pedido confirmado que se cancela. */
    void publicarPedidoCancelado(Long pedidoId, Long clienteId, String idempotencyKey);
}
