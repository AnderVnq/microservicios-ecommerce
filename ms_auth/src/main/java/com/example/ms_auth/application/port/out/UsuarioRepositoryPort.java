package com.example.ms_auth.application.port.out;

import com.example.ms_auth.domain.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepositoryPort {

    List<Usuario> findAll();

    Optional<Usuario> findById(Long id);

    boolean existsByKeycloakUserId(String keycloakUserId);

    Usuario save(Usuario usuario);
}
