package com.example.ms_auth.application.port.out;

/**
 * Consultas de autorizacion optimizadas: evitan traer el grafo completo
 * (usuario->roles->permisos o aplicacion->permisos) solo para responder
 * true/false.
 */
public interface AutorizacionRepositoryPort {

    boolean usuarioTienePermiso(String keycloakUserId, String recurso, String accion);

    boolean aplicacionTienePermiso(String keycloakClientId, String recurso, String accion);
}
