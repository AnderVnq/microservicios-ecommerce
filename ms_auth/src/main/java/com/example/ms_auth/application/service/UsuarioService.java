package com.example.ms_auth.application.service;

import com.example.ms_auth.domain.exception.RolNotFoundException;
import com.example.ms_auth.domain.exception.UsuarioAlreadyExistsException;
import com.example.ms_auth.domain.exception.UsuarioNotFoundException;
import com.example.ms_auth.domain.model.Usuario;
import com.example.ms_auth.application.port.in.UsuarioUseCase;
import com.example.ms_auth.application.port.out.RolRepositoryPort;
import com.example.ms_auth.application.port.out.UsuarioRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService implements UsuarioUseCase {

    private final UsuarioRepositoryPort usuarioRepository;
    private final RolRepositoryPort rolRepository;

    @Override
    public List<Usuario> findAll() {
        return usuarioRepository.findAll();
    }

    @Override
    public Usuario findById(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNotFoundException(id));
    }

    @Override
    @Transactional
    public Usuario create(String keycloakUserId, String nombres, String apellidos, String email) {
        if (usuarioRepository.existsByKeycloakUserId(keycloakUserId)) {
            throw new UsuarioAlreadyExistsException(keycloakUserId);
        }
        return usuarioRepository.save(Usuario.createNew(keycloakUserId, nombres, apellidos, email));
    }

    @Override
    @Transactional
    public Usuario update(Long id, String nombres, String apellidos, String email) {
        Usuario usuario = findById(id);
        usuario.actualizarDatos(nombres, apellidos, email);
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Usuario usuario = findById(id);
        usuario.desactivar();
        usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public Usuario asignarRol(Long usuarioId, Long rolId) {
        Usuario usuario = findById(usuarioId);
        if (rolRepository.findById(rolId).isEmpty()) {
            throw new RolNotFoundException(rolId);
        }
        Set<Long> roles = new HashSet<>(usuario.getRolIds());
        roles.add(rolId);
        usuario.setRolIds(roles);
        return usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public Usuario quitarRol(Long usuarioId, Long rolId) {
        Usuario usuario = findById(usuarioId);
        Set<Long> roles = new HashSet<>(usuario.getRolIds());
        roles.remove(rolId);
        usuario.setRolIds(roles);
        return usuarioRepository.save(usuario);
    }
}
