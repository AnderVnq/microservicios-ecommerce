package com.example.ms_auth.application.service;

import com.example.ms_auth.domain.exception.PermisoAlreadyExistsException;
import com.example.ms_auth.domain.exception.PermisoNotFoundException;
import com.example.ms_auth.domain.model.Permiso;
import com.example.ms_auth.application.port.in.PermisoUseCase;
import com.example.ms_auth.application.port.out.PermisoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermisoService implements PermisoUseCase {

    private final PermisoRepositoryPort permisoRepository;

    @Override
    public List<Permiso> findAll() {
        return permisoRepository.findAll();
    }

    @Override
    public Permiso findById(Long id) {
        return permisoRepository.findById(id)
                .orElseThrow(() -> new PermisoNotFoundException(id));
    }

    @Override
    @Transactional
    public Permiso create(String recurso, String accion, String descripcion) {
        if (permisoRepository.existsByRecursoAndAccion(recurso, accion)) {
            throw new PermisoAlreadyExistsException(recurso, accion);
        }
        return permisoRepository.save(Permiso.createNew(recurso, accion, descripcion));
    }
}
