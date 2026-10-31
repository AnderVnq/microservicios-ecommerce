package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.MarketSkuKey;
import com.example.mspedidos.domain.model.OrderItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SpringDataOrderItemRepository extends JpaRepository<OrderItemJpaEntity, Long> {

    Optional<OrderItemJpaEntity> findByIdAndOrderId(Long id, Long orderId);

    List<OrderItemJpaEntity> findByOrderIdOrderByIdAsc(Long orderId);

    List<OrderItemJpaEntity> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

    @Query("""
            select i from OrderItemJpaEntity i
            where (:releasePending is null or i.inventoryReleasePending = :releasePending)
              and (:linkPending is null
                   or (:linkPending = true and i.sku is null)
                   or (:linkPending = false and i.sku is not null))
            order by i.id asc
            """)
    List<OrderItemJpaEntity> search(@Param("releasePending") Boolean releasePending,
                                    @Param("linkPending") Boolean linkPending);

    @Query("""
            select distinct new com.example.mspedidos.application.port.out.MarketSkuKey(o.marketId, i.marketSku)
            from OrderItemJpaEntity i, OrderJpaEntity o
            where i.orderId = o.id and i.sku is null and i.marketSku is not null
              and i.status not in :terminal
            order by o.marketId, i.marketSku
            """)
    List<MarketSkuKey> findPendingMarketSkus(@Param("terminal") Collection<OrderItemStatus> terminal);

    @Query("""
            select i.id from OrderItemJpaEntity i, OrderJpaEntity o
            where i.orderId = o.id and o.marketId = :marketId and i.marketSku = :marketSku
              and i.sku is null and i.status not in :terminal
            order by i.id asc
            """)
    List<Long> findPendingIds(@Param("marketId") Long marketId, @Param("marketSku") String marketSku,
                              @Param("terminal") Collection<OrderItemStatus> terminal);

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

    @Query("""
            select i.id from OrderItemJpaEntity i, OrderJpaEntity o
            where i.orderId = o.id and i.sku = :sku
              and i.status = com.example.mspedidos.domain.model.OrderItemStatus.PROCESSING
              and i.purchaseId is null and i.inventoryUnitId is null
              and i.inventoryReleasePending = false
              and not exists (select 1 from StatusChangeRequestJpaEntity r
                              where r.orderItemId = i.id
                                and r.status = com.example.mspedidos.domain.model.StatusChangeRequestStatus.PENDING)
            order by case when o.shippingDeadline is null then 1 else 0 end, o.shippingDeadline asc,
                     o.orderDate asc, i.id asc
            """)
    List<Long> findCoverableIds(@Param("sku") String sku, Pageable page);

    @Query("""
            select i.id from OrderItemJpaEntity i, OrderJpaEntity o
            where i.orderId = o.id and i.id in :ids
            order by case when o.shippingDeadline is null then 1 else 0 end, o.shippingDeadline asc,
                     o.orderDate asc, i.id asc
            """)
    List<Long> orderIdsByUrgency(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderItemJpaEntity i where i.id in :ids order by i.id asc")
    List<OrderItemJpaEntity> findAllByIdsForUpdate(@Param("ids") Collection<Long> ids);

    List<OrderItemJpaEntity> findByPurchaseIdOrderByIdAsc(Long purchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderItemJpaEntity i where i.purchaseId = :purchaseId order by i.id asc")
    List<OrderItemJpaEntity> findAllByPurchaseIdForUpdate(@Param("purchaseId") Long purchaseId);
}
