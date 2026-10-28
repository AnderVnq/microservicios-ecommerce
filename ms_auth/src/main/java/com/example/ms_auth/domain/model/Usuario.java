package com.example.ms_auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

/**
 * Usuario interno (empleado de la empresa) que usa la app. Se identifica con
 * el "sub" del JWT emitido por Keycloak; las credenciales viven solo ahi,
 * aca solo se guarda el perfil de negocio y los roles que determinan que
 * puede hacer y ver.
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Usuario {

    private Long id;
    private String keycloakUserId;
    private String nombres;
    private String apellidos;
    private String email;
    private boolean activo;
    private Set<Long> rolIds;
    private Instant createdAt;
    private Instant updatedAt;

    public static Usuario createNew(String keycloakUserId, String nombres, String apellidos, String email) {
        return Usuario.builder()
                .keycloakUserId(keycloakUserId)
                .nombres(nombres)
                .apellidos(apellidos)
                .email(email)
                .activo(true)
                .rolIds(Set.of())
                .build();
    }

    public void actualizarDatos(String nombres, String apellidos, String email) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.email = email;
    }

    public void desactivar() {
        this.activo = false;
    }
}
