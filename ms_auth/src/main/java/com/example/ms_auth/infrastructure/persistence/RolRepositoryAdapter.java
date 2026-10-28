package com.example.ms_auth.infrastructure.persistence;

import com.example.ms_auth.domain.model.Rol;
import com.example.ms_auth.application.port.out.RolRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
class RolRepositoryAdapter implements RolRepositoryPort {

    private final SpringDataRolRepository jpaRepository;
    private final SpringDataPermisoRepository permisoJpaRepository;

    @Override
    public List<Rol> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Rol> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Rol> findAllByIds(List<Long> ids) {
        return jpaRepository.findAllByIdIn(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByNombre(String nombre) {
        return jpaRepository.existsByNombre(nombre);
    }

    @Override
    public Rol save(Rol rol) {
        Set<PermisoJpaEntity> permisos = new HashSet<>(
                permisoJpaRepository.findAllByIdIn(rol.getPermisoIds().stream().toList()));

        RolJpaEntity entity = RolJpaEntity.builder()
                .id(rol.getId())
                .nombre(rol.getNombre())
                .descripcion(rol.getDescripcion())
                .permisos(permisos)
                .build();

        return toDomain(jpaRepository.save(entity));
    }

    private Rol toDomain(RolJpaEntity entity) {
        return Rol.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .descripcion(entity.getDescripcion())
                .permisoIds(entity.getPermisos().stream().map(PermisoJpaEntity::getId).collect(java.util.stream.Collectors.toSet()))
                .build();
    }
}
