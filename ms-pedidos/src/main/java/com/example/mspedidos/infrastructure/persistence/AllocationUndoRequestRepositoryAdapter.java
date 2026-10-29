package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.AllocationUndoRequestRepositoryPort;
import com.example.mspedidos.domain.model.AllocationUndoRequest;
import com.example.mspedidos.domain.model.UndoRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class AllocationUndoRequestRepositoryAdapter implements AllocationUndoRequestRepositoryPort {

    private final SpringDataAllocationUndoRequestRepository jpaRepository;

    @Override
    public AllocationUndoRequest save(AllocationUndoRequest request) {
        return toDomain(jpaRepository.save(toEntity(request)));
    }

    @Override
    public Optional<AllocationUndoRequest> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<AllocationUndoRequest> findByIdForUpdate(Long id) {
        return jpaRepository.findForUpdate(id).map(this::toDomain);
    }

    @Override
    public Optional<AllocationUndoRequest> findPendingByOrderItemId(Long orderItemId) {
        return jpaRepository.findFirstByOrderItemIdAndStatus(orderItemId, UndoRequestStatus.PENDING)
                .map(this::toDomain);
    }

    @Override
    public boolean existsPendingForOrder(Long orderId) {
        return jpaRepository.existsForOrder(orderId, UndoRequestStatus.PENDING);
    }

    @Override
    public List<AllocationUndoRequest> search(UndoRequestStatus status) {
        return jpaRepository.search(status).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AllocationUndoRequest> findLatestApproved(Long orderItemId) {
        return jpaRepository.findFirstByOrderItemIdAndStatusOrderByDecidedAtDescIdDesc(orderItemId,
                UndoRequestStatus.APPROVED).map(this::toDomain);
    }

    private AllocationUndoRequest toDomain(AllocationUndoRequestJpaEntity e) {
        return AllocationUndoRequest.builder()
                .id(e.getId())
                .orderItemId(e.getOrderItemId())
                .inventoryUnitId(e.getInventoryUnitId())
                .reason(e.getReason())
                .status(e.getStatus())
                .requestedBy(e.getRequestedBy())
                .decidedBy(e.getDecidedBy())
                .decisionNote(e.getDecisionNote())
                .requestedAt(e.getRequestedAt())
                .decidedAt(e.getDecidedAt())
                .build();
    }

    private AllocationUndoRequestJpaEntity toEntity(AllocationUndoRequest r) {
        return AllocationUndoRequestJpaEntity.builder()
                .id(r.getId())
                .orderItemId(r.getOrderItemId())
                .inventoryUnitId(r.getInventoryUnitId())
                .reason(r.getReason())
                .status(r.getStatus())
                .requestedBy(r.getRequestedBy())
                .decidedBy(r.getDecidedBy())
                .decisionNote(r.getDecisionNote())
                .requestedAt(r.getRequestedAt())
                .decidedAt(r.getDecidedAt())
                .build();
    }
}
