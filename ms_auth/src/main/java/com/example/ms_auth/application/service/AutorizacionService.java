package com.example.ms_auth.application.service;

import com.example.ms_auth.application.port.in.AutorizacionUseCase;
import com.example.ms_auth.application.port.out.AutorizacionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Primero busca el subject como Usuario (sub de un humano); si no esta ahi,
 * lo busca como AplicacionExterna (azp de una app externa). Si no aparece en
 * ninguna, el default es denegar (fail closed): un subject desconocido no
 * tiene permisos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutorizacionService implements AutorizacionUseCase {

    private final AutorizacionRepositoryPort autorizacionRepository;

    @Override
    public boolean evaluar(String subject, String recurso, String accion) {
        if (autorizacionRepository.usuarioTienePermiso(subject, recurso, accion)) {
            return true;
        }
        return autorizacionRepository.aplicacionTienePermiso(subject, recurso, accion);
    }
}
