package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.domain.model.ImporterType;
import jakarta.validation.constraints.NotNull;

public record AssignImporterRequest(@NotNull ImporterType importerType, Long familyMemberId) {

    public AssignImporterCommand toCommand() {
        return new AssignImporterCommand(importerType, familyMemberId);
    }
}
