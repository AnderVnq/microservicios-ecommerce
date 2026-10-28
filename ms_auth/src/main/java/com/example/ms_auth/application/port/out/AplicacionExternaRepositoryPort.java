package com.example.ms_auth.application.port.out;

import com.example.ms_auth.domain.model.AplicacionExterna;

import java.util.List;
import java.util.Optional;

public interface AplicacionExternaRepositoryPort {

    List<AplicacionExterna> findAll();

    Optional<AplicacionExterna> findById(Long id);

    boolean existsByKeycloakClientId(String keycloakClientId);

    AplicacionExterna save(AplicacionExterna aplicacionExterna);
}
