package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class StatusChangeRequestRepositoryAdapter implements StatusChangeRequestRepositoryPort {

    private final SpringDataStatusChangeRequestRepository jpaRepository;

    @Override
    public StatusChangeRequest save(StatusChangeRequest request) {
        return toDomain(jpaRepository.save(toEntity(request)));
    }

    @Override
    public Optional<StatusChangeRequest> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<StatusChangeRequest> findByIdForUpdate(Long id) {
        return jpaRepository.findForUpdate(id).map(this::toDomain);
    }

    @Override
    public Optional<StatusChangeRequest> findPendingByOrderItemId(Long orderItemId) {
        return jpaRepository.findFirstByOrderItemIdAndStatus(orderItemId, StatusChangeRequestStatus.PENDING)
                .map(this::toDomain);
    }

    @Override
    public Optional<StatusChangeRequest> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public boolean existsPendingForOrder(Long orderId) {
        return jpaRepository.existsForOrder(orderId, StatusChangeRequestStatus.PENDING);
    }

    @Override
    public List<StatusChangeRequest> search(StatusChangeRequestStatus status) {
        return search(status, null);
    }

    @Override
    public List<StatusChangeRequest> search(StatusChangeRequestStatus status, StatusChangeRequestType type) {
        return jpaRepository.search(status, type).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<StatusChangeRequest> findPendingByPurchaseId(Long purchaseId) {
        return jpaRepository.findFirstByPurchaseIdAndStatus(purchaseId, StatusChangeRequestStatus.PENDING)
                .map(this::toDomain);
    }

    @Override
    public int sumPendingNotReceived(Long purchaseId) {
        return (int) jpaRepository.sumQuantity(purchaseId, StatusChangeRequestStatus.PENDING,
                StatusChangeRequestType.PURCHASE_NOT_RECEIVED);
    }

    @Override
    public int sumPendingNotReceivedExcluding(Long purchaseId, Long excludeRequestId) {
        if (excludeRequestId == null) {
            return sumPendingNotReceived(purchaseId);
        }
        return (int) jpaRepository.sumQuantityExcluding(purchaseId, StatusChangeRequestStatus.PENDING,
                StatusChangeRequestType.PURCHASE_NOT_RECEIVED, excludeRequestId);
    }

    @Override
    public Optional<StatusChangeRequest> findLatestApproved(Long orderItemId) {
        return jpaRepository.findFirstByOrderItemIdAndStatusOrderByDecidedAtDescIdDesc(orderItemId,
                StatusChangeRequestStatus.APPROVED).map(this::toDomain);
    }

    private StatusChangeRequest toDomain(StatusChangeRequestJpaEntity e) {
        return StatusChangeRequest.builder()
                .id(e.getId())
                .type(e.getType())
                .orderItemId(e.getOrderItemId())
                .purchaseId(e.getPurchaseId())
                .quantity(e.getQuantity())
                .inventoryUnitId(e.getInventoryUnitId())
                .reason(e.getReason())
                .status(e.getStatus())
                .requestedBy(e.getRequestedBy())
                .decidedBy(e.getDecidedBy())
                .decisionNote(e.getDecisionNote())
                .requestedAt(e.getRequestedAt())
                .decidedAt(e.getDecidedAt())
                .idempotencyKey(e.getIdempotencyKey())
                .requestHash(e.getRequestHash())
                .build();
    }

    private StatusChangeRequestJpaEntity toEntity(StatusChangeRequest r) {
        return StatusChangeRequestJpaEntity.builder()
                .id(r.getId())
                .type(r.getType())
                .orderItemId(r.getOrderItemId())
                .purchaseId(r.getPurchaseId())
                .quantity(r.getQuantity())
                .inventoryUnitId(r.getInventoryUnitId())
                .reason(r.getReason())
                .status(r.getStatus())
                .requestedBy(r.getRequestedBy())
                .decidedBy(r.getDecidedBy())
                .decisionNote(r.getDecisionNote())
                .requestedAt(r.getRequestedAt())
                .decidedAt(r.getDecidedAt())
                .idempotencyKey(r.getIdempotencyKey())
                .requestHash(r.getRequestHash())
                .build();
    }
}
