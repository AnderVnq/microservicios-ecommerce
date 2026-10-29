package com.example.mspedidos.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SpringDataPurchaseRepository extends JpaRepository<PurchaseJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseJpaEntity p where p.id = :id")
    Optional<PurchaseJpaEntity> findForUpdate(@Param("id") Long id);

    Optional<PurchaseJpaEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsBySupplierIdAndInvoiceNumber(Long supplierId, String invoiceNumber);

    boolean existsBySupplierIdAndInvoiceNumberAndIdNot(Long supplierId, String invoiceNumber, Long id);

    /** {@code sync} es OK, PENDING, FAILED o null (derivado de las tareas de sincronizacion). */
    @Query("""
            select p from PurchaseJpaEntity p
            where (:supplierId is null or p.supplierId = :supplierId)
              and (:sku is null or p.sku = :sku)
              and (:invoice is null or p.invoiceNumber = :invoice)
              and (:supplierPurchaseId is null or p.supplierPurchaseId = :supplierPurchaseId)
              and (:pending is null
                   or (:pending = true and p.quantity - p.receivedQuantity - p.notReceivedQuantity > 0)
                   or (:pending = false and p.quantity - p.receivedQuantity - p.notReceivedQuantity <= 0))
              and (:sync is null
                   or (:sync = 'FAILED' and exists (select 1 from InventorySyncTaskJpaEntity t
                        where t.purchaseId = p.id
                          and t.status = com.example.mspedidos.domain.model.InventorySyncTaskStatus.FAILED))
                   or (:sync = 'PENDING'
                        and not exists (select 1 from InventorySyncTaskJpaEntity t
                          where t.purchaseId = p.id
                            and t.status = com.example.mspedidos.domain.model.InventorySyncTaskStatus.FAILED)
                        and exists (select 1 from InventorySyncTaskJpaEntity t
                          where t.purchaseId = p.id
                            and t.status = com.example.mspedidos.domain.model.InventorySyncTaskStatus.PENDING))
                   or (:sync = 'OK'
                        and not exists (select 1 from InventorySyncTaskJpaEntity t
                          where t.purchaseId = p.id
                            and t.status <> com.example.mspedidos.domain.model.InventorySyncTaskStatus.DONE)))
            order by p.id desc
            """)
    List<PurchaseJpaEntity> search(@Param("supplierId") Long supplierId, @Param("sku") String sku,
                                   @Param("invoice") String invoice,
                                   @Param("supplierPurchaseId") String supplierPurchaseId,
                                   @Param("pending") Boolean pending, @Param("sync") String sync);
}
