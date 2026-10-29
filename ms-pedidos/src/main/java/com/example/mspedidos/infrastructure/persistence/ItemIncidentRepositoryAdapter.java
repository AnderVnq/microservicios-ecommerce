package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class ItemIncidentRepositoryAdapter implements ItemIncidentRepositoryPort {

    private final SpringDataItemIncidentRepository jpaRepository;

    @Override
    public ItemIncident save(ItemIncident incident) {
        return toDomain(jpaRepository.save(toEntity(incident)));
    }

    @Override
    public Optional<ItemIncident> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ItemIncident> findOpen(Long orderItemId, IncidentType type) {
        return jpaRepository.findFirstByOrderItemIdAndTypeAndStatus(orderItemId, type, IncidentStatus.OPEN)
                .map(this::toDomain);
    }

    @Override
    public List<ItemIncident> search(IncidentStatus status, IncidentType type, IncidentScope scope) {
        return jpaRepository.search(status, type, scope).stream().map(this::toDomain).toList();
    }

    private ItemIncident toDomain(ItemIncidentJpaEntity e) {
        return ItemIncident.builder()
                .id(e.getId())
                .orderItemId(e.getOrderItemId())
                .type(e.getType())
                .scope(e.getScope())
                .description(e.getDescription())
                .status(e.getStatus())
                .openedBy(e.getOpenedBy())
                .resolvedBy(e.getResolvedBy())
                .openedAt(e.getOpenedAt())
                .resolvedAt(e.getResolvedAt())
                .build();
    }

    private ItemIncidentJpaEntity toEntity(ItemIncident i) {
        return ItemIncidentJpaEntity.builder()
                .id(i.getId())
                .orderItemId(i.getOrderItemId())
                .type(i.getType())
                .scope(i.getScope())
                .description(i.getDescription())
                .status(i.getStatus())
                .openedBy(i.getOpenedBy())
                .resolvedBy(i.getResolvedBy())
                .openedAt(i.getOpenedAt())
                .resolvedAt(i.getResolvedAt())
                .build();
    }
}
