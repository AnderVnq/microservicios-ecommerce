package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort;
import com.example.msinventory.domain.model.InventoryUnit;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class InventoryUnitRepositoryAdapter implements InventoryUnitRepositoryPort {

    private final SpringDataInventoryUnitRepository repository;
    private final EntityManager entityManager;

    InventoryUnitRepositoryAdapter(SpringDataInventoryUnitRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public InventoryUnit save(InventoryUnit unit) {
        return toDomain(repository.save(toEntity(unit)));
    }

    @Override
    public List<InventoryUnit> saveAll(List<InventoryUnit> units) {
        return units.stream().map(this::save).toList();
    }

    @Override
    public Optional<InventoryUnit> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<InventoryUnit> findByIdForUpdate(Long id) {
        return repository.findForUpdate(id).map(this::toDomain);
    }

    @Override
    public List<InventoryUnit> findByEntryId(Long entryId) {
        return repository.findByEntryIdOrderByIdAsc(entryId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<InventoryUnit> search(UnitFilter f) {
        return repository.search(f.sku(), f.status(), f.warehouse(), f.conditionCode(), f.returnDeadlineBefore())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<InventoryUnit> findByAllocatedOrderItemId(Long orderItemId) {
        return repository.findByAllocatedOrderItemId(orderItemId).map(this::toDomain);
    }

    /** Si la unidad ya existe se actualiza la fila gestionada (conserva @Version); si no, se crea. */
    private InventoryUnitJpaEntity toEntity(InventoryUnit u) {
        InventoryUnitJpaEntity e = u.getId() == null ? new InventoryUnitJpaEntity()
                : repository.findById(u.getId()).orElseGet(InventoryUnitJpaEntity::new);
        e.setSku(u.getSku());
        e.setCondition(entityManager.getReference(ItemConditionJpaEntity.class, u.getConditionCode()));
        e.setWarehouse(u.getWarehouse());
        e.setStatus(u.getStatus());
        e.setUnitCost(u.getUnitCost());
        e.setCostCurrency(u.getCostCurrency());
        e.setEntryType(u.getEntryType());
        e.setInvoiceNumber(u.getInvoiceNumber());
        e.setSupplierPurchaseId(u.getSupplierPurchaseId());
        e.setSupplierId(u.getSupplierId());
        e.setSourceReference(u.getSourceReference());
        e.setExpectedArrivalDate(u.getExpectedArrivalDate());
        e.setArrivedAt(u.getArrivedAt());
        e.setSupplierReturnDeadline(u.getSupplierReturnDeadline());
        e.setAllocatedOrderItemId(u.getAllocatedOrderItemId());
        e.setEntry(entityManager.getReference(InventoryEntryJpaEntity.class, u.getEntryId()));
        e.setCreatedAt(u.getCreatedAt());
        e.setUpdatedAt(u.getUpdatedAt());
        return e;
    }

    private InventoryUnit toDomain(InventoryUnitJpaEntity e) {
        return InventoryUnit.builder()
                .id(e.getId())
                .sku(e.getSku())
                .conditionCode(e.getCondition().getCode())
                .warehouse(e.getWarehouse())
                .status(e.getStatus())
                .unitCost(e.getUnitCost())
                .costCurrency(e.getCostCurrency())
                .entryType(e.getEntryType())
                .invoiceNumber(e.getInvoiceNumber())
                .supplierPurchaseId(e.getSupplierPurchaseId())
                .supplierId(e.getSupplierId())
                .sourceReference(e.getSourceReference())
                .expectedArrivalDate(e.getExpectedArrivalDate())
                .arrivedAt(e.getArrivedAt())
                .supplierReturnDeadline(e.getSupplierReturnDeadline())
                .allocatedOrderItemId(e.getAllocatedOrderItemId())
                .entryId(e.getEntry().getId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
