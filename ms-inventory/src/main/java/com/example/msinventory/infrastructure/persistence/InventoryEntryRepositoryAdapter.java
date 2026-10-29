package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.application.port.out.InventoryEntryRepositoryPort;
import com.example.msinventory.domain.model.InventoryEntry;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class InventoryEntryRepositoryAdapter implements InventoryEntryRepositoryPort {

    private final SpringDataInventoryEntryRepository repository;
    private final SpringDataInventoryUnitRepository unitRepository;

    InventoryEntryRepositoryAdapter(SpringDataInventoryEntryRepository repository,
                                    SpringDataInventoryUnitRepository unitRepository) {
        this.repository = repository;
        this.unitRepository = unitRepository;
    }

    @Override
    public InventoryEntry save(InventoryEntry entry) {
        return toDomain(repository.save(toEntity(entry)));
    }

    @Override
    public Optional<InventoryEntry> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public Optional<InventoryEntry> findByPurchaseId(Long purchaseId) {
        return repository.findByPurchaseId(purchaseId).map(this::toDomain);
    }

    @Override
    public boolean existsOtherWithInvoice(String invoiceNumber) {
        return unitRepository.existsByInvoiceNumber(invoiceNumber);
    }

    private InventoryEntryJpaEntity toEntity(InventoryEntry e) {
        return InventoryEntryJpaEntity.builder()
                .id(e.getId())
                .idempotencyKey(e.getIdempotencyKey())
                .requestHash(e.getRequestHash())
                .entryType(e.getEntryType())
                .quantity(e.getQuantity())
                .performedBy(e.getPerformedBy())
                .purchaseId(e.getPurchaseId())
                .supplierId(e.getSupplierId())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private InventoryEntry toDomain(InventoryEntryJpaEntity e) {
        return InventoryEntry.builder()
                .id(e.getId())
                .idempotencyKey(e.getIdempotencyKey())
                .requestHash(e.getRequestHash())
                .entryType(e.getEntryType())
                .quantity(e.getQuantity())
                .performedBy(e.getPerformedBy())
                .purchaseId(e.getPurchaseId())
                .supplierId(e.getSupplierId())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
