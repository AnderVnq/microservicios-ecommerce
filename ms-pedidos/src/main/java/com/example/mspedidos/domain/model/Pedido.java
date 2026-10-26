package com.example.mspedidos.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un pedido lo genera siempre un cliente titular (clienteId) o uno de sus
 * familiares asociados (familiarId, opcional). Cuando requiereImportacion es
 * true, el pedido consume una unidad del cupo de importaciones del titular
 * (maximo 3, compartido con sus familiares) gestionado por ms-clientes.
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Pedido {

    private Long id;
    private Long marketId;
    private Long clienteId;
    private Long familiarId;
    private boolean requiereImportacion;
    private EstadoPedido estado;
    private BigDecimal total;
    private String moneda;
    private Instant createdAt;
    private Instant updatedAt;

    public static Pedido nuevo(Long marketId, Long clienteId, Long familiarId,
                                boolean requiereImportacion, BigDecimal total, String moneda) {
        return Pedido.builder()
                .marketId(marketId)
                .clienteId(clienteId)
                .familiarId(familiarId)
                .requiereImportacion(requiereImportacion)
                .estado(EstadoPedido.PENDIENTE)
                .total(total)
                .moneda(moneda)
                .build();
    }

    public String idempotencyKeyCupo() {
        return "pedido:" + id;
    }

    public void confirmar() {
        this.estado = EstadoPedido.CONFIRMADO;
    }

    public void cambiarEstado(EstadoPedido nuevoEstado) {
        this.estado = nuevoEstado;
    }

    public void cancelar() {
        this.estado = EstadoPedido.CANCELADO;
    }
}
