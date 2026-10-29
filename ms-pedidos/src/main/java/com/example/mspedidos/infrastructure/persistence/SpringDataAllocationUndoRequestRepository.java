package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.UndoRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SpringDataAllocationUndoRequestRepository extends JpaRepository<AllocationUndoRequestJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AllocationUndoRequestJpaEntity r where r.id = :id")
    Optional<AllocationUndoRequestJpaEntity> findForUpdate(@Param("id") Long id);

    Optional<AllocationUndoRequestJpaEntity> findFirstByOrderItemIdAndStatus(Long orderItemId,
                                                                             UndoRequestStatus status);

    Optional<AllocationUndoRequestJpaEntity> findFirstByOrderItemIdAndStatusOrderByDecidedAtDescIdDesc(
            Long orderItemId, UndoRequestStatus status);

    @Query("""
            select count(r) > 0 from AllocationUndoRequestJpaEntity r, OrderItemJpaEntity i
            where r.orderItemId = i.id and i.orderId = :orderId and r.status = :status
            """)
    boolean existsForOrder(@Param("orderId") Long orderId, @Param("status") UndoRequestStatus status);

    @Query("""
            select r from AllocationUndoRequestJpaEntity r
            where (:status is null or r.status = :status)
            order by r.requestedAt desc, r.id desc
            """)
    List<AllocationUndoRequestJpaEntity> search(@Param("status") UndoRequestStatus status);
}
