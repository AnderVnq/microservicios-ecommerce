package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentType;
import jakarta.validation.constraints.NotNull;

public record OpenIncidentRequest(@NotNull IncidentType type, @NotNull IncidentScope scope, String description) {
}
