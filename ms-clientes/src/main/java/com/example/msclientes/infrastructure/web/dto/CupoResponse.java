package com.example.msclientes.infrastructure.web.dto;

import com.example.msclientes.domain.model.Cliente;

public record CupoResponse(
        Long clienteId,
        int maxImportaciones,
        int importacionesUsadas,
        int cupoDisponible
) {
    public static CupoResponse from(Cliente c) {
        return new CupoResponse(c.getId(), c.getMaxImportaciones(), c.getImportacionesUsadas(), c.getCupoDisponible());
    }
}
