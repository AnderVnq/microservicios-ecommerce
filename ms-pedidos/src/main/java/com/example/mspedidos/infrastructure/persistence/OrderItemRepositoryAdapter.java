package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class OrderItemRepositoryAdapter implements OrderItemRepositoryPort {

    private final SpringDataOrderItemRepository jpaRepository;

    @Override
    public OrderItem save(OrderItem item) {
        return toDomain(jpaRepository.save(toEntity(item)));
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> items) {
        return jpaRepository.saveAll(items.stream().map(this::toEntity).toList())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<OrderItem> findById(Long itemId) {
        return jpaRepository.findById(itemId).map(this::toDomain);
    }

    @Override
    public Optional<OrderItem> findByIdAndOrderId(Long itemId, Long orderId) {
        return jpaRepository.findByIdAndOrderId(itemId, orderId).map(this::toDomain);
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<OrderItem> findByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByOrderIdInOrderByIdAsc(orderIds).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<OrderItem> findByIdAndOrderIdForUpdate(Long itemId, Long orderId) {
        return jpaRepository.findForUpdate(itemId, orderId).map(this::toDomain);
    }

    @Override
    public List<OrderItem> findByOrderIdForUpdate(Long orderId) {
        return jpaRepository.findAllForUpdate(orderId).stream().map(this::toDomain).toList();
    }

    @Override
    public long countQuotaUnits(ImporterDocument document, int year, Long excludeItemId) {
        return jpaRepository.countQuotaUnits(document.type(), document.number(),
                LocalDate.of(year, 1, 1).atStartOfDay(), LocalDate.of(year + 1, 1, 1).atStartOfDay(),
                excludeItemId);
    }

    @Override
    public List<OrderItem> findReleasePending() {
        return search(true);
    }

    @Override
    public List<OrderItem> search(Boolean inventoryReleasePending) {
        List<OrderItemJpaEntity> found = inventoryReleasePending == null
                ? jpaRepository.findAll(Sort.by("id"))
                : jpaRepository.findByInventoryReleasePendingOrderByIdAsc(inventoryReleasePending);
        return found.stream().map(this::toDomain).toList();
    }

    @Override
    public List<Long> findCoverableIdsByUrgency(String sku, int limit) {
        if (limit < 1) {
            return List.of();
        }
        return jpaRepository.findCoverableIds(sku, PageRequest.of(0, limit));
    }

    @Override
    public List<OrderItem> findByIdsForUpdate(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByIdsForUpdate(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<OrderItem> findByPurchaseId(Long purchaseId) {
        return jpaRepository.findByPurchaseIdOrderByIdAsc(purchaseId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<OrderItem> findByPurchaseIdForUpdate(Long purchaseId) {
        return jpaRepository.findAllByPurchaseIdForUpdate(purchaseId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Long> orderByUrgency(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.orderIdsByUrgency(itemIds);
    }

    private OrderItem toDomain(OrderItemJpaEntity e) {
        ImporterDocument doc = e.getImporterDocumentType() == null ? null
                : new ImporterDocument(e.getImporterDocumentType(), e.getImporterDocumentNumber());
        return OrderItem.builder()
                .id(e.getId())
                .orderId(e.getOrderId())
                .sku(e.getSku())
                .externalItemId(e.getExternalItemId())
                .unitPrice(e.getUnitPrice())
                .status(e.getStatus())
                .warehouse(e.getWarehouse())
                .deliveredTo(e.getDeliveredTo())
                .importerType(e.getImporterType())
                .familyMemberId(e.getFamilyMemberId())
                .importerDocument(doc)
                .inventoryUnitId(e.getInventoryUnitId())
                .purchaseId(e.getPurchaseId())
                .inventoryReleasePending(e.isInventoryReleasePending())
                .pendingReleaseUnitId(e.getPendingReleaseUnitId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private OrderItemJpaEntity toEntity(OrderItem i) {
        ImporterDocument doc = i.getImporterDocument();
        return OrderItemJpaEntity.builder()
                .id(i.getId())
                .orderId(i.getOrderId())
                .sku(i.getSku())
                .externalItemId(i.getExternalItemId())
                .unitPrice(i.getUnitPrice())
                .status(i.getStatus())
                .warehouse(i.getWarehouse())
                .deliveredTo(i.getDeliveredTo())
                .importerType(i.getImporterType())
                .familyMemberId(i.getFamilyMemberId())
                .importerDocumentType(doc == null ? null : doc.type())
                .importerDocumentNumber(doc == null ? null : doc.number())
                .inventoryUnitId(i.getInventoryUnitId())
                .purchaseId(i.getPurchaseId())
                .inventoryReleasePending(i.isInventoryReleasePending())
                .pendingReleaseUnitId(i.getPendingReleaseUnitId())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
