package com.example.ms_auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Atomo de autorizacion: recurso + accion (ej: recurso=pedidos, accion=CANCELAR).
 * No hace falta mas granularidad que esta.
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Permiso {

    private Long id;
    private String recurso;
    private String accion;
    private String descripcion;

    public static Permiso createNew(String recurso, String accion, String descripcion) {
        return Permiso.builder()
                .recurso(recurso)
                .accion(accion)
                .descripcion(descripcion)
                .build();
    }
}
