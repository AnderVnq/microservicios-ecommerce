package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.ImporterType;

/** {@code familyMemberId} solo aplica a {@code FAMILY_MEMBER}; {@code UNASSIGNED} se rechaza. */
public record AssignImporterCommand(ImporterType importerType, Long familyMemberId) {
}
