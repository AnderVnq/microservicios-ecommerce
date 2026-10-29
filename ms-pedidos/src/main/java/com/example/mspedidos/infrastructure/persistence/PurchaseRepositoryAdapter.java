package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.PurchaseFilter;
import com.example.mspedidos.application.port.out.PurchaseRepositoryPort;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.PurchaseArrival;
import com.example.mspedidos.domain.model.PurchaseNonReceipt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class PurchaseRepositoryAdapter implements PurchaseRepositoryPort {

    private final SpringDataPurchaseRepository purchaseRepository;
    private final SpringDataPurchaseArrivalRepository arrivalRepository;
    private final SpringDataPurchaseNonReceiptRepository nonReceiptRepository;

    @Override
    public Purchase save(Purchase purchase) {
        return toDomain(purchaseRepository.save(toEntity(purchase)));
    }

    @Override
    public Optional<Purchase> findById(Long id) {
        return purchaseRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Purchase> findByIdForUpdate(Long id) {
        return purchaseRepository.findForUpdate(id).map(this::toDomain);
    }

    @Override
    public Optional<Purchase> findByIdempotencyKey(String key) {
        return purchaseRepository.findByIdempotencyKey(key).map(this::toDomain);
    }

    @Override
    public boolean existsOtherWithInvoice(Long supplierId, String invoiceNumber, Long excludeId) {
        return excludeId == null
                ? purchaseRepository.existsBySupplierIdAndInvoiceNumber(supplierId, invoiceNumber)
                : purchaseRepository.existsBySupplierIdAndInvoiceNumberAndIdNot(supplierId, invoiceNumber, excludeId);
    }

    @Override
    public List<Purchase> search(PurchaseFilter f) {
        return purchaseRepository.search(f.supplierId(), f.sku(), f.invoiceNumber(), f.supplierPurchaseId(),
                f.pending(), f.inventorySync() == null ? null : f.inventorySync().name())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public PurchaseArrival saveArrival(PurchaseArrival a) {
        return toDomain(arrivalRepository.save(PurchaseArrivalJpaEntity.builder()
                .id(a.getId())
                .purchaseId(a.getPurchaseId())
                .idempotencyKey(a.getIdempotencyKey())
                .requestHash(a.getRequestHash())
                .quantity(a.getQuantity())
                .orderItemIds(join(a.getOrderItemIds()))
                .surplusQuantity(a.getSurplusQuantity())
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt())
                .build()));
    }

    @Override
    public Optional<PurchaseArrival> findArrivalByKey(String idempotencyKey) {
        return arrivalRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public List<PurchaseArrival> findArrivals(Long purchaseId) {
        return arrivalRepository.findByPurchaseIdOrderByIdAsc(purchaseId).stream().map(this::toDomain).toList();
    }

    @Override
    public PurchaseNonReceipt saveNonReceipt(PurchaseNonReceipt n) {
        return toDomain(nonReceiptRepository.save(PurchaseNonReceiptJpaEntity.builder()
                .id(n.getId())
                .purchaseId(n.getPurchaseId())
                .quantity(n.getQuantity())
                .orderItemIds(join(n.getOrderItemIds()))
                .surplusQuantity(n.getSurplusQuantity())
                .reason(n.getReason())
                .statusChangeRequestId(n.getStatusChangeRequestId())
                .appliedBy(n.getAppliedBy())
                .appliedAt(n.getAppliedAt())
                .idempotencyKey(n.getIdempotencyKey())
                .requestHash(n.getRequestHash())
                .build()));
    }

    @Override
    public Optional<PurchaseNonReceipt> findNonReceiptByKey(String idempotencyKey) {
        return nonReceiptRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public List<PurchaseNonReceipt> findNonReceipts(Long purchaseId) {
        return nonReceiptRepository.findByPurchaseIdOrderByIdAsc(purchaseId).stream().map(this::toDomain).toList();
    }

    private static String join(List<Long> ids) {
        return ids == null ? "" : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Long> split(String ids) {
        return ids == null || ids.isBlank() ? List.of()
                : Arrays.stream(ids.split(",")).map(String::trim).map(Long::valueOf).toList();
    }

    private PurchaseArrival toDomain(PurchaseArrivalJpaEntity e) {
        return PurchaseArrival.builder()
                .id(e.getId())
                .purchaseId(e.getPurchaseId())
                .idempotencyKey(e.getIdempotencyKey())
                .requestHash(e.getRequestHash())
                .quantity(e.getQuantity())
                .orderItemIds(split(e.getOrderItemIds()))
                .surplusQuantity(e.getSurplusQuantity())
                .createdBy(e.getCreatedBy())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private PurchaseNonReceipt toDomain(PurchaseNonReceiptJpaEntity e) {
        return PurchaseNonReceipt.builder()
                .id(e.getId())
                .purchaseId(e.getPurchaseId())
                .quantity(e.getQuantity())
                .orderItemIds(split(e.getOrderItemIds()))
                .surplusQuantity(e.getSurplusQuantity())
                .reason(e.getReason())
                .statusChangeRequestId(e.getStatusChangeRequestId())
                .appliedBy(e.getAppliedBy())
                .appliedAt(e.getAppliedAt())
                .idempotencyKey(e.getIdempotencyKey())
                .requestHash(e.getRequestHash())
                .build();
    }

    private Purchase toDomain(PurchaseJpaEntity e) {
        return Purchase.builder()
                .id(e.getId())
                .idempotencyKey(e.getIdempotencyKey())
                .requestHash(e.getRequestHash())
                .supplierId(e.getSupplierId())
                .sku(e.getSku())
                .conditionCode(e.getConditionCode())
                .quantity(e.getQuantity())
                .unitCost(e.getUnitCost())
                .costCurrency(e.getCostCurrency())
                .supplierPurchaseId(e.getSupplierPurchaseId())
                .invoiceNumber(e.getInvoiceNumber())
                .expectedArrivalDate(e.getExpectedArrivalDate())
                .receivedQuantity(e.getReceivedQuantity())
                .notReceivedQuantity(e.getNotReceivedQuantity())
                .inventoryEntryId(e.getInventoryEntryId())
                .createdBy(e.getCreatedBy())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private PurchaseJpaEntity toEntity(Purchase p) {
        return PurchaseJpaEntity.builder()
                .id(p.getId())
                .idempotencyKey(p.getIdempotencyKey())
                .requestHash(p.getRequestHash())
                .supplierId(p.getSupplierId())
                .sku(p.getSku())
                .conditionCode(p.getConditionCode())
                .quantity(p.getQuantity())
                .unitCost(p.getUnitCost())
                .costCurrency(p.getCostCurrency())
                .supplierPurchaseId(p.getSupplierPurchaseId())
                .invoiceNumber(p.getInvoiceNumber())
                .expectedArrivalDate(p.getExpectedArrivalDate())
                .receivedQuantity(p.getReceivedQuantity())
                .notReceivedQuantity(p.getNotReceivedQuantity())
                .inventoryEntryId(p.getInventoryEntryId())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
