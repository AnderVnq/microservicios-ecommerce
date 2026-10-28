package com.example.ms_auth.application.service;

import com.example.ms_auth.domain.exception.AplicacionExternaAlreadyExistsException;
import com.example.ms_auth.domain.exception.AplicacionExternaNotFoundException;
import com.example.ms_auth.domain.exception.PermisoNotFoundException;
import com.example.ms_auth.domain.model.AplicacionExterna;
import com.example.ms_auth.application.port.in.AplicacionExternaUseCase;
import com.example.ms_auth.application.port.out.AplicacionExternaRepositoryPort;
import com.example.ms_auth.application.port.out.PermisoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AplicacionExternaService implements AplicacionExternaUseCase {

    private final AplicacionExternaRepositoryPort aplicacionExternaRepository;
    private final PermisoRepositoryPort permisoRepository;

    @Override
    public List<AplicacionExterna> findAll() {
        return aplicacionExternaRepository.findAll();
    }

    @Override
    public AplicacionExterna findById(Long id) {
        return aplicacionExternaRepository.findById(id)
                .orElseThrow(() -> new AplicacionExternaNotFoundException(id));
    }

    @Override
    @Transactional
    public AplicacionExterna create(String keycloakClientId, String nombre, String empresa) {
        if (aplicacionExternaRepository.existsByKeycloakClientId(keycloakClientId)) {
            throw new AplicacionExternaAlreadyExistsException(keycloakClientId);
        }
        return aplicacionExternaRepository.save(AplicacionExterna.createNew(keycloakClientId, nombre, empresa));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        AplicacionExterna aplicacion = findById(id);
        aplicacion.desactivar();
        aplicacionExternaRepository.save(aplicacion);
    }

    @Override
    @Transactional
    public AplicacionExterna asignarPermiso(Long aplicacionId, Long permisoId) {
        AplicacionExterna aplicacion = findById(aplicacionId);
        if (permisoRepository.findById(permisoId).isEmpty()) {
            throw new PermisoNotFoundException(permisoId);
        }
        Set<Long> permisos = new HashSet<>(aplicacion.getPermisoIds());
        permisos.add(permisoId);
        aplicacion.setPermisoIds(permisos);
        return aplicacionExternaRepository.save(aplicacion);
    }

    @Override
    @Transactional
    public AplicacionExterna quitarPermiso(Long aplicacionId, Long permisoId) {
        AplicacionExterna aplicacion = findById(aplicacionId);
        Set<Long> permisos = new HashSet<>(aplicacion.getPermisoIds());
        permisos.remove(permisoId);
        aplicacion.setPermisoIds(permisos);
        return aplicacionExternaRepository.save(aplicacion);
    }
}
