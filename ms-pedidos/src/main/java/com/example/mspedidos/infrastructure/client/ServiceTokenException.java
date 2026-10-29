package com.example.mspedidos.infrastructure.client;

/** No se pudo obtener el token de servicio (Keycloak client_credentials). */
class ServiceTokenException extends RuntimeException {

    ServiceTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
