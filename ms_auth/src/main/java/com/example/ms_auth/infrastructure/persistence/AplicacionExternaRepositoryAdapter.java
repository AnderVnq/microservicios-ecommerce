package com.example.ms_auth.infrastructure.persistence;

import com.example.ms_auth.domain.model.AplicacionExterna;
import com.example.ms_auth.application.port.out.AplicacionExternaRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class AplicacionExternaRepositoryAdapter implements AplicacionExternaRepositoryPort {

    private final SpringDataAplicacionExternaRepository jpaRepository;
    private final SpringDataPermisoRepository permisoJpaRepository;

    @Override
    public List<AplicacionExterna> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AplicacionExterna> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByKeycloakClientId(String keycloakClientId) {
        return jpaRepository.existsByKeycloakClientId(keycloakClientId);
    }

    @Override
    public AplicacionExterna save(AplicacionExterna aplicacionExterna) {
        Set<PermisoJpaEntity> permisos = new HashSet<>(
                permisoJpaRepository.findAllByIdIn(aplicacionExterna.getPermisoIds().stream().toList()));

        AplicacionExternaJpaEntity entity = AplicacionExternaJpaEntity.builder()
                .id(aplicacionExterna.getId())
                .keycloakClientId(aplicacionExterna.getKeycloakClientId())
                .nombre(aplicacionExterna.getNombre())
                .empresa(aplicacionExterna.getEmpresa())
                .activa(aplicacionExterna.isActiva())
                .permisos(permisos)
                .createdAt(aplicacionExterna.getCreatedAt())
                .updatedAt(aplicacionExterna.getUpdatedAt())
                .build();

        return toDomain(jpaRepository.save(entity));
    }

    private AplicacionExterna toDomain(AplicacionExternaJpaEntity entity) {
        return AplicacionExterna.builder()
                .id(entity.getId())
                .keycloakClientId(entity.getKeycloakClientId())
                .nombre(entity.getNombre())
                .empresa(entity.getEmpresa())
                .activa(entity.isActiva())
                .permisoIds(entity.getPermisos().stream().map(PermisoJpaEntity::getId).collect(Collectors.toSet()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
