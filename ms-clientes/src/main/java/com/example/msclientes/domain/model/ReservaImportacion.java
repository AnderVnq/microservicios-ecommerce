package com.example.msclientes.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Registra el consumo de una unidad del cupo de importaciones de un {@link Cliente}
 * (titular o alguno de sus familiares) para un pedido puntual. La unicidad de
 * idempotencyKey evita que un reintento de ms-pedidos duplique el consumo del cupo.
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class ReservaImportacion {

    private Long id;
    private Long clienteId;
    private Long pedidoId;
    private String idempotencyKey;
    private EstadoReserva estado;
    private Instant createdAt;

    public static ReservaImportacion activa(Long clienteId, Long pedidoId, String idempotencyKey) {
        return ReservaImportacion.builder()
                .clienteId(clienteId)
                .pedidoId(pedidoId)
                .idempotencyKey(idempotencyKey)
                .estado(EstadoReserva.ACTIVA)
                .build();
    }

    public boolean estaActiva() {
        return estado == EstadoReserva.ACTIVA;
    }

    public void liberar() {
        this.estado = EstadoReserva.LIBERADA;
    }

    public enum EstadoReserva {
        ACTIVA,
        LIBERADA
    }
}
