package com.example.ms_auth.application.port.in;

import com.example.ms_auth.domain.model.Rol;

import java.util.List;

public interface RolUseCase {

    List<Rol> findAll();

    Rol findById(Long id);

    Rol create(String nombre, String descripcion);

    Rol asignarPermiso(Long rolId, Long permisoId);

    Rol quitarPermiso(Long rolId, Long permisoId);
}
