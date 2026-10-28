package com.example.ms_auth.infrastructure.persistence;

import com.example.ms_auth.domain.model.Usuario;
import com.example.ms_auth.application.port.out.UsuarioRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class UsuarioRepositoryAdapter implements UsuarioRepositoryPort {

    private final SpringDataUsuarioRepository jpaRepository;
    private final SpringDataRolRepository rolJpaRepository;

    @Override
    public List<Usuario> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Usuario> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByKeycloakUserId(String keycloakUserId) {
        return jpaRepository.existsByKeycloakUserId(keycloakUserId);
    }

    @Override
    public Usuario save(Usuario usuario) {
        Set<RolJpaEntity> roles = new HashSet<>(
                rolJpaRepository.findAllByIdIn(usuario.getRolIds().stream().toList()));

        UsuarioJpaEntity entity = UsuarioJpaEntity.builder()
                .id(usuario.getId())
                .keycloakUserId(usuario.getKeycloakUserId())
                .nombres(usuario.getNombres())
                .apellidos(usuario.getApellidos())
                .email(usuario.getEmail())
                .activo(usuario.isActivo())
                .roles(roles)
                .createdAt(usuario.getCreatedAt())
                .updatedAt(usuario.getUpdatedAt())
                .build();

        return toDomain(jpaRepository.save(entity));
    }

    private Usuario toDomain(UsuarioJpaEntity entity) {
        return Usuario.builder()
                .id(entity.getId())
                .keycloakUserId(entity.getKeycloakUserId())
                .nombres(entity.getNombres())
                .apellidos(entity.getApellidos())
                .email(entity.getEmail())
                .activo(entity.isActivo())
                .rolIds(entity.getRoles().stream().map(RolJpaEntity::getId).collect(Collectors.toSet()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
