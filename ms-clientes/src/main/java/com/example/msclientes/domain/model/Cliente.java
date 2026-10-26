package com.example.msclientes.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Cliente titular. No pertenece a un unico market: puede generar pedidos en
 * distintos markets, pero su cupo de importaciones es global y se comparte
 * con sus familiares asociados (ver {@link Familiar}).
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Cliente {

    private Long id;
    private String nombres;
    private String apellidos;
    private String tipoDocumento;
    private String numeroDocumento;
    private String email;
    private String telefono;

    /** Cupo maximo de importaciones (regla de negocio actual: 3), compartido con familiares. */
    private int maxImportaciones;

    /** Importaciones ya consumidas por el titular y sus familiares. */
    private int importacionesUsadas;

    private boolean activo;
    private Instant createdAt;
    private Instant updatedAt;

    public static Cliente createNew(String nombres, String apellidos, String tipoDocumento,
                                     String numeroDocumento, String email, String telefono,
                                     int maxImportaciones) {
        return Cliente.builder()
                .nombres(nombres)
                .apellidos(apellidos)
                .tipoDocumento(tipoDocumento)
                .numeroDocumento(numeroDocumento)
                .email(email)
                .telefono(telefono)
                .maxImportaciones(maxImportaciones)
                .importacionesUsadas(0)
                .activo(true)
                .build();
    }

    public int getCupoDisponible() {
        return maxImportaciones - importacionesUsadas;
    }

    public void actualizarDatos(String nombres, String apellidos, String email, String telefono) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.email = email;
        this.telefono = telefono;
    }

    public void desactivar() {
        this.activo = false;
    }
}
