package com.example.msclientes.application.port.in;

import com.example.msclientes.domain.model.Cliente;
import com.example.msclientes.domain.model.ReservaImportacion;

/**
 * Regla de negocio central: el cupo de importaciones (por defecto 3) es del
 * cliente titular y se comparte entre el titular y todos sus familiares
 * asociados. ms-pedidos consulta/reserva/libera este cupo antes y despues
 * de confirmar o cancelar un pedido que requiera importacion.
 */
public interface CupoImportacionUseCase {

    Cliente consultarCupo(Long clienteId);

    /**
     * Reserva una unidad de cupo de forma idempotente. idempotencyKey debe
     * derivarse del pedido (ej: "pedido:123"), nunca de un UUID generado en
     * cada intento, para que los reintentos sean seguros.
     */
    ReservaImportacion reservar(Long clienteId, Long pedidoId, String idempotencyKey);

    void liberar(String idempotencyKey);
}
