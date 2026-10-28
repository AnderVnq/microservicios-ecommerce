package com.example.ms_auth.application.port.in;

import com.example.ms_auth.domain.model.AplicacionExterna;

import java.util.List;

public interface AplicacionExternaUseCase {

    List<AplicacionExterna> findAll();

    AplicacionExterna findById(Long id);

    AplicacionExterna create(String keycloakClientId, String nombre, String empresa);

    void deactivate(Long id);

    AplicacionExterna asignarPermiso(Long aplicacionId, Long permisoId);

    AplicacionExterna quitarPermiso(Long aplicacionId, Long permisoId);
}
