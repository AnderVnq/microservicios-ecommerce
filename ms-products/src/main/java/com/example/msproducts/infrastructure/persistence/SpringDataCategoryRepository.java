package com.example.msproducts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SpringDataCategoryRepository extends JpaRepository<CategoryJpaEntity, Long> {

    // parentId null genera IS NULL; el nombre se compara con la collation de la columna
    Optional<CategoryJpaEntity> findByParentIdAndName(Long parentId, String name);

    List<CategoryJpaEntity> findAllByOrderByLevelAscNameAsc();
}
