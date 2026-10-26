package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(
        @NotNull EstadoPedido estado
) {
}
