package com.example.msclientes.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Familiar asociado a un cliente titular. Puede generar pedidos propios,
 * identificado por el titular (clienteId).
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Familiar {

    private Long id;
    private Long clienteId;
    private String nombres;
    private String apellidos;
    private String tipoDocumento;
    private String numeroDocumento;
    private Parentesco parentesco;
    private boolean activo;
    private Instant createdAt;
    private Instant updatedAt;

    public static Familiar createNew(Long clienteId, String nombres, String apellidos,
                                      String tipoDocumento, String numeroDocumento, Parentesco parentesco) {
        return Familiar.builder()
                .clienteId(clienteId)
                .nombres(nombres)
                .apellidos(apellidos)
                .tipoDocumento(tipoDocumento)
                .numeroDocumento(numeroDocumento)
                .parentesco(parentesco)
                .activo(true)
                .build();
    }

    public void desactivar() {
        this.activo = false;
    }
}
