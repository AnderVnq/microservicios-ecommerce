package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.application.port.out.InventoryMovementRepositoryPort;
import com.example.msinventory.domain.model.InventoryMovement;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
class InventoryMovementRepositoryAdapter implements InventoryMovementRepositoryPort {

    private final SpringDataInventoryMovementRepository repository;

    InventoryMovementRepositoryAdapter(SpringDataInventoryMovementRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(InventoryMovement m) {
        repository.save(InventoryMovementJpaEntity.builder()
                .unitId(m.unitId())
                .type(m.type())
                .direction(m.type().direction())
                .sku(m.sku())
                .conditionCode(m.conditionCode())
                .unitCost(m.unitCost())
                .costCurrency(m.costCurrency())
                .fromWarehouse(m.fromWarehouse())
                .toWarehouse(m.toWarehouse())
                .reference(m.reference())
                .reason(m.reason())
                .performedBy(m.performedBy())
                .occurredAt(m.occurredAt())
                .build());
    }

    @Override
    public List<InventoryMovement> findByUnitId(Long unitId) {
        return repository.findByUnitIdOrderByOccurredAtAscIdAsc(unitId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<InventoryMovement> findForKardex(String sku, String conditionCode, String currency, Instant to) {
        return repository.findForKardex(sku, conditionCode, currency, to).stream().map(this::toDomain).toList();
    }

    private InventoryMovement toDomain(InventoryMovementJpaEntity e) {
        return new InventoryMovement(e.getId(), e.getUnitId(), e.getType(), e.getSku(), e.getConditionCode(),
                e.getUnitCost(), e.getCostCurrency(), e.getFromWarehouse(), e.getToWarehouse(), e.getReference(),
                e.getReason(), e.getPerformedBy(), e.getOccurredAt());
    }
}
