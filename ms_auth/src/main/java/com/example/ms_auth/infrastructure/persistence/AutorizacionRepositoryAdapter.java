package com.example.ms_auth.infrastructure.persistence;

import com.example.ms_auth.application.port.out.AutorizacionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AutorizacionRepositoryAdapter implements AutorizacionRepositoryPort {

    private final SpringDataUsuarioRepository usuarioJpaRepository;
    private final SpringDataAplicacionExternaRepository aplicacionJpaRepository;

    @Override
    public boolean usuarioTienePermiso(String keycloakUserId, String recurso, String accion) {
        return usuarioJpaRepository.tienePermiso(keycloakUserId, recurso, accion);
    }

    @Override
    public boolean aplicacionTienePermiso(String keycloakClientId, String recurso, String accion) {
        return aplicacionJpaRepository.tienePermiso(keycloakClientId, recurso, accion);
    }
}
