package com.example.ms_auth.application.port.out;

import com.example.ms_auth.domain.model.Permiso;

import java.util.List;
import java.util.Optional;

public interface PermisoRepositoryPort {

    List<Permiso> findAll();

    Optional<Permiso> findById(Long id);

    List<Permiso> findAllByIds(List<Long> ids);

    boolean existsByRecursoAndAccion(String recurso, String accion);

    Permiso save(Permiso permiso);
}
