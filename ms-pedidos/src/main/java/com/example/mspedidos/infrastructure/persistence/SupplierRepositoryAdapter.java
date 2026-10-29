package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.SupplierRepositoryPort;
import com.example.mspedidos.domain.model.Supplier;
import com.example.mspedidos.domain.model.SupplierStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class SupplierRepositoryAdapter implements SupplierRepositoryPort {

    private final SpringDataSupplierRepository jpaRepository;

    @Override
    public Supplier save(Supplier supplier) {
        return toDomain(jpaRepository.save(toEntity(supplier)));
    }

    @Override
    public Optional<Supplier> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name, Long excludeId) {
        String normalized = SupplierJpaEntity.normalize(name);
        return excludeId == null
                ? jpaRepository.existsByNameNormalized(normalized)
                : jpaRepository.existsByNameNormalizedAndIdNot(normalized, excludeId);
    }

    @Override
    public List<Supplier> search(SupplierStatus status, String country, String nameContains) {
        String name = nameContains == null || nameContains.isBlank() ? null
                : SupplierJpaEntity.normalize(nameContains);
        return jpaRepository.search(status, country, name).stream().map(this::toDomain).toList();
    }

    private Supplier toDomain(SupplierJpaEntity e) {
        return Supplier.builder()
                .id(e.getId())
                .name(e.getName())
                .status(e.getStatus())
                .country(e.getCountry())
                .address(e.getAddress())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private SupplierJpaEntity toEntity(Supplier s) {
        return SupplierJpaEntity.builder()
                .id(s.getId())
                .name(s.getName())
                .status(s.getStatus())
                .country(s.getCountry())
                .address(s.getAddress())
                .description(s.getDescription())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
