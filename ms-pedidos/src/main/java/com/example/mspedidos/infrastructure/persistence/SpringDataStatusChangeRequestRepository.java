package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SpringDataStatusChangeRequestRepository extends JpaRepository<StatusChangeRequestJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StatusChangeRequestJpaEntity r where r.id = :id")
    Optional<StatusChangeRequestJpaEntity> findForUpdate(@Param("id") Long id);

    Optional<StatusChangeRequestJpaEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<StatusChangeRequestJpaEntity> findFirstByOrderItemIdAndStatus(Long orderItemId,
                                                                           StatusChangeRequestStatus status);

    Optional<StatusChangeRequestJpaEntity> findFirstByPurchaseIdAndStatus(Long purchaseId,
                                                                          StatusChangeRequestStatus status);

    Optional<StatusChangeRequestJpaEntity> findFirstByOrderItemIdAndStatusOrderByDecidedAtDescIdDesc(
            Long orderItemId, StatusChangeRequestStatus status);

    @Query("""
            select count(r) > 0 from StatusChangeRequestJpaEntity r, OrderItemJpaEntity i
            where r.orderItemId = i.id and i.orderId = :orderId and r.status = :status
            """)
    boolean existsForOrder(@Param("orderId") Long orderId, @Param("status") StatusChangeRequestStatus status);

    @Query("""
            select r from StatusChangeRequestJpaEntity r
            where (:status is null or r.status = :status) and (:type is null or r.type = :type)
            order by r.requestedAt desc, r.id desc
            """)
    List<StatusChangeRequestJpaEntity> search(@Param("status") StatusChangeRequestStatus status,
                                              @Param("type") StatusChangeRequestType type);

    @Query("""
            select coalesce(sum(r.quantity), 0) from StatusChangeRequestJpaEntity r
            where r.purchaseId = :purchaseId and r.status = :status and r.type = :type
            """)
    long sumQuantity(@Param("purchaseId") Long purchaseId, @Param("status") StatusChangeRequestStatus status,
                     @Param("type") StatusChangeRequestType type);

    @Query("""
            select coalesce(sum(r.quantity), 0) from StatusChangeRequestJpaEntity r
            where r.purchaseId = :purchaseId and r.status = :status and r.type = :type and r.id <> :excludeId
            """)
    long sumQuantityExcluding(@Param("purchaseId") Long purchaseId,
                              @Param("status") StatusChangeRequestStatus status,
                              @Param("type") StatusChangeRequestType type, @Param("excludeId") Long excludeId);
}
