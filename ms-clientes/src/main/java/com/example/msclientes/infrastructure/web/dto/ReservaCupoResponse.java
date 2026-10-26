package com.example.msclientes.infrastructure.web.dto;

import com.example.msclientes.domain.model.ReservaImportacion;

public record ReservaCupoResponse(
        Long id,
        Long clienteId,
        Long pedidoId,
        String idempotencyKey,
        String estado
) {
    public static ReservaCupoResponse from(ReservaImportacion r) {
        return new ReservaCupoResponse(r.getId(), r.getClienteId(), r.getPedidoId(), r.getIdempotencyKey(), r.getEstado().name());
    }
}
