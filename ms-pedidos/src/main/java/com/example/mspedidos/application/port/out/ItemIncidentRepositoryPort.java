package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;

import java.util.List;
import java.util.Optional;

public interface ItemIncidentRepositoryPort {

    ItemIncident save(ItemIncident incident);

    Optional<ItemIncident> findById(Long id);

    Optional<ItemIncident> findOpen(Long orderItemId, IncidentType type);

    /** Filtros null significan "cualquiera". */
    List<ItemIncident> search(IncidentStatus status, IncidentType type, IncidentScope scope);
}
