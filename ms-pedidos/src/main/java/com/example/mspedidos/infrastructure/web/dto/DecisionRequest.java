package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de aprobar ({@code note} opcional) y rechazar ({@code note} obligatoria, grupo {@link Reject}). */
public record DecisionRequest(@NotBlank(groups = DecisionRequest.Reject.class) String note) {

    /** Grupo de validacion del rechazo. */
    public interface Reject {
    }
}
