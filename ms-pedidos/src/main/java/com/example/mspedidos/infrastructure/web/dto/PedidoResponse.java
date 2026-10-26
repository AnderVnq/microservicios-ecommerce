package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.Pedido;

import java.math.BigDecimal;
import java.time.Instant;

public record PedidoResponse(
        Long id,
        Long marketId,
        Long clienteId,
        Long familiarId,
        boolean requiereImportacion,
        String estado,
        BigDecimal total,
        String moneda,
        Instant createdAt,
        Instant updatedAt
) {
    public static PedidoResponse from(Pedido p) {
        return new PedidoResponse(
                p.getId(), p.getMarketId(), p.getClienteId(), p.getFamiliarId(),
                p.isRequiereImportacion(), p.getEstado().name(), p.getTotal(), p.getMoneda(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
