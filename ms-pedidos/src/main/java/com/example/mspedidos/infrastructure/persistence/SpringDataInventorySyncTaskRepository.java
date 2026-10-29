package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SpringDataInventorySyncTaskRepository extends JpaRepository<InventorySyncTaskJpaEntity, Long> {

    List<InventorySyncTaskJpaEntity> findByStatusOrderByIdAsc(InventorySyncTaskStatus status);

    List<InventorySyncTaskJpaEntity> findByPurchaseIdOrderByIdAsc(Long purchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InventorySyncTaskJpaEntity t where t.id = :id")
    Optional<InventorySyncTaskJpaEntity> findForUpdate(@Param("id") Long id);
}
