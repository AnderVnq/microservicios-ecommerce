package com.example.ms_auth.application.port.in;

/**
 * Puerto que consultan el api-gateway o los microservicios (Opcion A de la
 * guia): dado el "subject" del JWT (sub de un usuario o azp de una app
 * externa), responde si tiene permiso para recurso+accion. Si el subject no
 * se reconoce en ninguna de las dos tablas, el resultado es false (fail closed).
 */
public interface AutorizacionUseCase {

    boolean evaluar(String subject, String recurso, String accion);
}
