package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.application.port.out.PurchaseOperationRepositoryPort;
import com.example.msinventory.domain.model.PurchaseOperation;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
class PurchaseOperationRepositoryAdapter implements PurchaseOperationRepositoryPort {

    private final SpringDataPurchaseOperationRepository repository;

    PurchaseOperationRepositoryAdapter(SpringDataPurchaseOperationRepository repository) {
        this.repository = repository;
    }

    @Override
    public PurchaseOperation save(PurchaseOperation o) {
        return toDomain(repository.saveAndFlush(PurchaseOperationJpaEntity.builder()
                .id(o.id())
                .idempotencyKey(o.idempotencyKey())
                .requestHash(o.requestHash())
                .purchaseId(o.purchaseId())
                .type(o.type())
                .unitIds(o.unitIds().stream().map(String::valueOf).collect(Collectors.joining(",")))
                .createdAt(o.createdAt())
                .build()));
    }

    @Override
    public Optional<PurchaseOperation> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    private PurchaseOperation toDomain(PurchaseOperationJpaEntity e) {
        List<Long> ids = e.getUnitIds().isBlank() ? List.of()
                : Arrays.stream(e.getUnitIds().split(",")).map(Long::valueOf).toList();
        return new PurchaseOperation(e.getId(), e.getIdempotencyKey(), e.getRequestHash(), e.getPurchaseId(),
                e.getType(), ids, e.getCreatedAt());
    }
}
