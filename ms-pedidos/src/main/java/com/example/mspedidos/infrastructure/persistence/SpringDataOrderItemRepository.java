package com.example.mspedidos.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

interface SpringDataOrderItemRepository extends JpaRepository<OrderItemJpaEntity, Long> {

    Optional<OrderItemJpaEntity> findByIdAndOrderId(Long id, Long orderId);

    List<OrderItemJpaEntity> findByOrderIdOrderByIdAsc(Long orderId);

    List<OrderItemJpaEntity> findByInventoryReleasePendingOrderByIdAsc(boolean inventoryReleasePending);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderItemJpaEntity i where i.id = :id and i.orderId = :orderId")
    Optional<OrderItemJpaEntity> findForUpdate(@Param("id") Long id, @Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderItemJpaEntity i where i.orderId = :orderId order by i.id asc")
    List<OrderItemJpaEntity> findAllForUpdate(@Param("orderId") Long orderId);

    @Query("""
            select count(i) from OrderItemJpaEntity i, OrderJpaEntity o
            where i.orderId = o.id
              and i.status <> com.example.mspedidos.domain.model.OrderItemStatus.CANCELLED
              and i.importerType <> com.example.mspedidos.domain.model.ImporterType.UNASSIGNED
              and i.importerType <> com.example.mspedidos.domain.model.ImporterType.NOT_REQUIRED
              and i.importerDocumentType = :docType
              and i.importerDocumentNumber = :docNumber
              and o.orderDate >= :from and o.orderDate < :to
              and (:excludeItemId is null or i.id <> :excludeItemId)
            """)
    long countQuotaUnits(@Param("docType") String docType, @Param("docNumber") String docNumber,
                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                         @Param("excludeItemId") Long excludeItemId);
}
