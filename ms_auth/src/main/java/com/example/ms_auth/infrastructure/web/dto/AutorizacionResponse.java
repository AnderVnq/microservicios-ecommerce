package com.example.ms_auth.infrastructure.web.dto;

public record AutorizacionResponse(String subject, String recurso, String accion, boolean permitido) {
}
