package com.example.msclientes.infrastructure.web.dto;

import com.example.msclientes.domain.model.Familiar;

public record FamiliarResponse(
        Long id,
        Long clienteId,
        String nombres,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        String parentesco,
        boolean activo
) {
    public static FamiliarResponse from(Familiar f) {
        return new FamiliarResponse(
                f.getId(), f.getClienteId(), f.getNombres(), f.getApellidos(),
                f.getTipoDocumento(), f.getNumeroDocumento(), f.getParentesco().name(), f.isActivo()
        );
    }
}
