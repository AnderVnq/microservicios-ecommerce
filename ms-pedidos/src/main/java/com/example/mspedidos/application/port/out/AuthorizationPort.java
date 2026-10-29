package com.example.mspedidos.application.port.out;

/** Puerto hacia ms_auth: consulta de permisos (falla cerrado ante cualquier error). */
public interface AuthorizationPort {

    boolean isAllowed(String subject, String resource, String action);
}
