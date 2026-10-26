package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PedidoRequest(
        @NotNull Long marketId,
        /** Siempre el cliente titular, aunque el pedido lo haya pedido un familiar. */
        @NotNull Long clienteId,
        /** Opcional: id del familiar asociado que realizo el pedido. */
        Long familiarId,
        boolean requiereImportacion,
        @NotNull @DecimalMin("0.0") BigDecimal total,
        @NotNull @Size(min = 3, max = 3) String moneda
) {
}
