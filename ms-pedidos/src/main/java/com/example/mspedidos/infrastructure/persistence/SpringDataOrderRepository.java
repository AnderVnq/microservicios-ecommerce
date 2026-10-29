package com.example.mspedidos.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, Long> {

    boolean existsByMarketIdAndExternalOrderId(Long marketId, String externalOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderJpaEntity o where o.id = :id")
    Optional<OrderJpaEntity> findForUpdate(@Param("id") Long id);
}
