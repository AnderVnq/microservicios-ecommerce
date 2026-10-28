package com.example.ms_auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

/**
 * Un tercero que consume tus APIs via client_credentials en Keycloak.
 * Se identifica con el "azp"/client_id del JWT. A diferencia de un Usuario,
 * no tiene roles: se le asignan permisos directo (suele necesitar 2-3, no un
 * rol completo).
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class AplicacionExterna {

    private Long id;
    private String keycloakClientId;
    private String nombre;
    private String empresa;
    private boolean activa;
    private Set<Long> permisoIds;
    private Instant createdAt;
    private Instant updatedAt;

    public static AplicacionExterna createNew(String keycloakClientId, String nombre, String empresa) {
        return AplicacionExterna.builder()
                .keycloakClientId(keycloakClientId)
                .nombre(nombre)
                .empresa(empresa)
                .activa(true)
                .permisoIds(Set.of())
                .build();
    }

    public void desactivar() {
        this.activa = false;
    }
}
