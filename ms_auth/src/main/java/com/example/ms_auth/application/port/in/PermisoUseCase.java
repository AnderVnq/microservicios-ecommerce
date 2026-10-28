package com.example.ms_auth.application.port.in;

import com.example.ms_auth.domain.model.Permiso;

import java.util.List;

public interface PermisoUseCase {

    List<Permiso> findAll();

    Permiso findById(Long id);

    Permiso create(String recurso, String accion, String descripcion);
}
