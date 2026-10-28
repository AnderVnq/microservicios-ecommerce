package com.example.msclientes.application.port.out;

/** Puerto hacia el bus de eventos del saga: publica el resultado de una reserva de cupo. */
public interface CupoRespuestaPublisherPort {

    void publicarRespuesta(Long pedidoId, Long clienteId, String idempotencyKey, boolean exito, String motivo);
}
