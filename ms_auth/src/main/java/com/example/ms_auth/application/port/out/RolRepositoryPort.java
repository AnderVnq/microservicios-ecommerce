package com.example.ms_auth.application.port.out;

import com.example.ms_auth.domain.model.Rol;

import java.util.List;
import java.util.Optional;

public interface RolRepositoryPort {

    List<Rol> findAll();

    Optional<Rol> findById(Long id);

    List<Rol> findAllByIds(List<Long> ids);

    boolean existsByNombre(String nombre);

    Rol save(Rol rol);
}
