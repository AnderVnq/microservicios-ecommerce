package com.example.msclientes.infrastructure.web.dto;

import com.example.msclientes.domain.model.Cliente;

public record ClienteResponse(
        Long id,
        String nombres,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        String email,
        String telefono,
        boolean activo
) {
    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(
                c.getId(), c.getNombres(), c.getApellidos(), c.getTipoDocumento(), c.getNumeroDocumento(),
                c.getEmail(), c.getTelefono(), c.isActivo()
        );
    }
}
