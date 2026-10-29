package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.SupplierStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface SpringDataSupplierRepository extends JpaRepository<SupplierJpaEntity, Long> {

    boolean existsByNameNormalized(String nameNormalized);

    boolean existsByNameNormalizedAndIdNot(String nameNormalized, Long id);

    @Query("""
            select s from SupplierJpaEntity s
            where (:status is null or s.status = :status)
              and (:country is null or s.country = :country)
              and (:name is null or s.nameNormalized like concat('%', :name, '%'))
            order by s.name asc, s.id asc
            """)
    List<SupplierJpaEntity> search(@Param("status") SupplierStatus status, @Param("country") String country,
                                   @Param("name") String normalizedName);
}
