package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SpringDataItemIncidentRepository extends JpaRepository<ItemIncidentJpaEntity, Long> {

    Optional<ItemIncidentJpaEntity> findFirstByOrderItemIdAndTypeAndStatus(
            Long orderItemId, IncidentType type, IncidentStatus status);

    @Query("""
            select i from ItemIncidentJpaEntity i
            where (:status is null or i.status = :status)
              and (:type is null or i.type = :type)
              and (:scope is null or i.scope = :scope)
            order by i.openedAt desc, i.id desc
            """)
    List<ItemIncidentJpaEntity> search(@Param("status") IncidentStatus status,
                                       @Param("type") IncidentType type,
                                       @Param("scope") IncidentScope scope);
}
