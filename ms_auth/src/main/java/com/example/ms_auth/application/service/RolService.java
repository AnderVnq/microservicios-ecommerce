package com.example.ms_auth.application.service;

import com.example.ms_auth.domain.exception.PermisoNotFoundException;
import com.example.ms_auth.domain.exception.RolAlreadyExistsException;
import com.example.ms_auth.domain.exception.RolNotFoundException;
import com.example.ms_auth.domain.model.Rol;
import com.example.ms_auth.application.port.in.RolUseCase;
import com.example.ms_auth.application.port.out.PermisoRepositoryPort;
import com.example.ms_auth.application.port.out.RolRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RolService implements RolUseCase {

    private final RolRepositoryPort rolRepository;
    private final PermisoRepositoryPort permisoRepository;

    @Override
    public List<Rol> findAll() {
        return rolRepository.findAll();
    }

    @Override
    public Rol findById(Long id) {
        return rolRepository.findById(id)
                .orElseThrow(() -> new RolNotFoundException(id));
    }

    @Override
    @Transactional
    public Rol create(String nombre, String descripcion) {
        if (rolRepository.existsByNombre(nombre)) {
            throw new RolAlreadyExistsException(nombre);
        }
        return rolRepository.save(Rol.createNew(nombre, descripcion));
    }

    @Override
    @Transactional
    public Rol asignarPermiso(Long rolId, Long permisoId) {
        Rol rol = findById(rolId);
        if (permisoRepository.findById(permisoId).isEmpty()) {
            throw new PermisoNotFoundException(permisoId);
        }
        Set<Long> permisos = new HashSet<>(rol.getPermisoIds());
        permisos.add(permisoId);
        rol.setPermisoIds(permisos);
        return rolRepository.save(rol);
    }

    @Override
    @Transactional
    public Rol quitarPermiso(Long rolId, Long permisoId) {
        Rol rol = findById(rolId);
        Set<Long> permisos = new HashSet<>(rol.getPermisoIds());
        permisos.remove(permisoId);
        rol.setPermisoIds(permisos);
        return rolRepository.save(rol);
    }
}
