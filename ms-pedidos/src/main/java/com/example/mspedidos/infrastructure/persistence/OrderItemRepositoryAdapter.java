package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
    public Optional<OrderItem> findByIdAndOrderId(Long itemId, Long orderId) {
        return jpaRepository.findByIdAndOrderId(itemId, orderId).map(this::toDomain);
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(this::toDomain).toList();
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
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
