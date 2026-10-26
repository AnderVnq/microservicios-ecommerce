package com.example.mspedidos.application.port.out;

/**
 * Puerto hacia ms-clientes: consume/libera una unidad del cupo de
 * importaciones del cliente titular. idempotencyKey debe derivarse del
 * pedido (ver Pedido.idempotencyKeyCupo) para que los reintentos sean seguros.
 */
public interface CupoImportacionPort {

    void reservar(Long clienteId, Long pedidoId, String idempotencyKey);

    void liberar(Long clienteId, String idempotencyKey);
}
