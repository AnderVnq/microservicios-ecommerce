package com.example.ms_auth.application.port.in;

import com.example.ms_auth.domain.model.Usuario;

import java.util.List;

public interface UsuarioUseCase {

    List<Usuario> findAll();

    Usuario findById(Long id);

    Usuario create(String keycloakUserId, String nombres, String apellidos, String email);

    Usuario update(Long id, String nombres, String apellidos, String email);

    void deactivate(Long id);

    Usuario asignarRol(Long usuarioId, Long rolId);

    Usuario quitarRol(Long usuarioId, Long rolId);
}
