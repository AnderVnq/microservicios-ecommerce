package com.example.msinventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataItemConditionRepository extends JpaRepository<ItemConditionJpaEntity, String> {
}
