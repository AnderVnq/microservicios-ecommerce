package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class InventorySyncTaskRepositoryAdapter implements InventorySyncTaskRepositoryPort {

    private final SpringDataInventorySyncTaskRepository jpaRepository;

    @Override
    public InventorySyncTask save(InventorySyncTask task) {
        return toDomain(jpaRepository.save(toEntity(task)));
    }

    @Override
    public List<InventorySyncTask> findPendingOrdered() {
        return jpaRepository.findByStatusOrderByIdAsc(InventorySyncTaskStatus.PENDING)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<InventorySyncTask> findByPurchaseId(Long purchaseId) {
        return jpaRepository.findByPurchaseIdOrderByIdAsc(purchaseId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<InventorySyncTask> findByIdForUpdate(Long id) {
        return jpaRepository.findForUpdate(id).map(this::toDomain);
    }

    private InventorySyncTask toDomain(InventorySyncTaskJpaEntity e) {
        return InventorySyncTask.builder()
                .id(e.getId())
                .purchaseId(e.getPurchaseId())
                .type(e.getType())
                .payload(e.getPayload())
                .idempotencyKey(e.getIdempotencyKey())
                .status(e.getStatus())
                .attempts(e.getAttempts())
                .lastError(e.getLastError())
                .createdAt(e.getCreatedAt())
                .doneAt(e.getDoneAt())
                .build();
    }

    private InventorySyncTaskJpaEntity toEntity(InventorySyncTask t) {
        return InventorySyncTaskJpaEntity.builder()
                .id(t.getId())
                .purchaseId(t.getPurchaseId())
                .type(t.getType())
                .payload(t.getPayload())
                .idempotencyKey(t.getIdempotencyKey())
                .status(t.getStatus())
                .attempts(t.getAttempts())
                .lastError(t.getLastError())
                .createdAt(t.getCreatedAt())
                .doneAt(t.getDoneAt())
                .build();
    }
}
