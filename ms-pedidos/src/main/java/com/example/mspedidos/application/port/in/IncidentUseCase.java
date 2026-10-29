package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;

import java.util.List;

public interface IncidentUseCase {

    /** Filtros null significan "cualquiera". */
    List<ItemIncident> search(IncidentStatus status, IncidentType type, IncidentScope scope);

    ItemIncident open(Long orderId, Long itemId, IncidentType type, IncidentScope scope, String description);

    ItemIncident resolve(Long incidentId);
}
