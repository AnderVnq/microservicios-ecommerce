package com.example.ms_auth.infrastructure.persistence;

import com.example.ms_auth.domain.model.Permiso;
import com.example.ms_auth.application.port.out.PermisoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class PermisoRepositoryAdapter implements PermisoRepositoryPort {

    private final SpringDataPermisoRepository jpaRepository;

    @Override
    public List<Permiso> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Permiso> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Permiso> findAllByIds(List<Long> ids) {
        return jpaRepository.findAllByIdIn(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByRecursoAndAccion(String recurso, String accion) {
        return jpaRepository.existsByRecursoAndAccion(recurso, accion);
    }

    @Override
    public Permiso save(Permiso permiso) {
        PermisoJpaEntity saved = jpaRepository.save(toEntity(permiso));
        return toDomain(saved);
    }

    private Permiso toDomain(PermisoJpaEntity entity) {
        return Permiso.builder()
                .id(entity.getId())
                .recurso(entity.getRecurso())
                .accion(entity.getAccion())
                .descripcion(entity.getDescripcion())
                .build();
    }

    private PermisoJpaEntity toEntity(Permiso permiso) {
        return PermisoJpaEntity.builder()
                .id(permiso.getId())
                .recurso(permiso.getRecurso())
                .accion(permiso.getAccion())
                .descripcion(permiso.getDescripcion())
                .build();
    }
}
