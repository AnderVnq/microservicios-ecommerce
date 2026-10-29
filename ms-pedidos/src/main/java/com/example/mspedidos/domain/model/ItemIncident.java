package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.IncidentAlreadyResolvedException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class ItemIncident {

    private Long id;
    private Long orderItemId;
    private IncidentType type;
    private IncidentScope scope;
    private String description;
    private IncidentStatus status;
    private String openedBy;
    private String resolvedBy;
    private Instant openedAt;
    private Instant resolvedAt;

    public static ItemIncident importerPending(Long orderItemId, String actor) {
        return importerPending(orderItemId, "Unidad excede el cupo del titular; asignar importador", actor);
    }

    public static ItemIncident importerPending(Long orderItemId, String description, String actor) {
        return open(orderItemId, IncidentType.IMPORTER_ASSIGNMENT_PENDING, IncidentScope.EXTERNAL, description, actor);
    }

    public static ItemIncident open(Long orderItemId, IncidentType type, IncidentScope scope,
                                    String description, String actor) {
        return ItemIncident.builder()
                .orderItemId(orderItemId)
                .type(type)
                .scope(scope)
                .description(description)
                .status(IncidentStatus.OPEN)
                .openedBy(actor)
                .openedAt(Instant.now())
                .build();
    }

    public void resolve(String actor) {
        if (status == IncidentStatus.RESOLVED) {
            throw new IncidentAlreadyResolvedException(id);
        }
        this.status = IncidentStatus.RESOLVED;
        this.resolvedBy = actor;
        this.resolvedAt = Instant.now();
    }
}
