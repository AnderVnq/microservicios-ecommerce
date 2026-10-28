package com.example.ms_auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/** Agrupa permisos para no tener que asignarlos uno por uno a cada usuario. */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class Rol {

    private Long id;
    private String nombre;
    private String descripcion;
    private Set<Long> permisoIds;

    public static Rol createNew(String nombre, String descripcion) {
        return Rol.builder()
                .nombre(nombre)
                .descripcion(descripcion)
                .permisoIds(Set.of())
                .build();
    }
}
