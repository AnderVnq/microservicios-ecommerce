package com.example.msinventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface SpringDataInventoryMovementRepository extends JpaRepository<InventoryMovementJpaEntity, Long> {

    List<InventoryMovementJpaEntity> findByUnitIdOrderByOccurredAtAscIdAsc(Long unitId);

    @Query("""
            select m from InventoryMovementJpaEntity m
            where m.sku = :sku and m.costCurrency = :currency
              and (:conditionCode is null or m.conditionCode = :conditionCode)
              and (:to is null or m.occurredAt <= :to)
            order by m.occurredAt asc, m.id asc
            """)
    List<InventoryMovementJpaEntity> findForKardex(@Param("sku") String sku,
                                                   @Param("conditionCode") String conditionCode,
                                                   @Param("currency") String currency, @Param("to") Instant to);
}
