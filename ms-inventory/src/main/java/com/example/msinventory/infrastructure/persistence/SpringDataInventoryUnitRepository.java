package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface SpringDataInventoryUnitRepository extends JpaRepository<InventoryUnitJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from InventoryUnitJpaEntity u where u.id = :id")
    Optional<InventoryUnitJpaEntity> findForUpdate(@Param("id") Long id);

    List<InventoryUnitJpaEntity> findByEntryIdOrderByIdAsc(Long entryId);

    Optional<InventoryUnitJpaEntity> findByAllocatedOrderItemId(Long orderItemId);

    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("""
            select u from InventoryUnitJpaEntity u
            where (:sku is null or u.sku = :sku)
              and (:status is null or u.status = :status)
              and (:warehouse is null or u.warehouse = :warehouse)
              and (:conditionCode is null or u.condition.code = :conditionCode)
              and (:deadline is null or u.supplierReturnDeadline < :deadline)
            order by u.id asc
            """)
    List<InventoryUnitJpaEntity> search(@Param("sku") String sku, @Param("status") InventoryUnitStatus status,
                                        @Param("warehouse") Warehouse warehouse,
                                        @Param("conditionCode") String conditionCode,
                                        @Param("deadline") LocalDate deadline);
}
