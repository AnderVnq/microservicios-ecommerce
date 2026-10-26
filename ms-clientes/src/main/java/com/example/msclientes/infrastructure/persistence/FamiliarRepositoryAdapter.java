package com.example.msclientes.infrastructure.persistence;

import com.example.msclientes.domain.model.Familiar;
import com.example.msclientes.application.port.out.FamiliarRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class FamiliarRepositoryAdapter implements FamiliarRepositoryPort {

    private final SpringDataFamiliarRepository jpaRepository;

    @Override
    public List<Familiar> findByClienteId(Long clienteId) {
        return jpaRepository.findByClienteId(clienteId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Familiar> findByIdAndClienteId(Long id, Long clienteId) {
        return jpaRepository.findByIdAndClienteId(id, clienteId).map(this::toDomain);
    }

    @Override
    public Familiar save(Familiar familiar) {
        FamiliarJpaEntity saved = jpaRepository.save(toEntity(familiar));
        return toDomain(saved);
    }

    private Familiar toDomain(FamiliarJpaEntity entity) {
        return Familiar.builder()
                .id(entity.getId())
                .clienteId(entity.getClienteId())
                .nombres(entity.getNombres())
                .apellidos(entity.getApellidos())
                .tipoDocumento(entity.getTipoDocumento())
                .numeroDocumento(entity.getNumeroDocumento())
                .parentesco(entity.getParentesco())
                .activo(entity.isActivo())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private FamiliarJpaEntity toEntity(Familiar familiar) {
        return FamiliarJpaEntity.builder()
                .id(familiar.getId())
                .clienteId(familiar.getClienteId())
                .nombres(familiar.getNombres())
                .apellidos(familiar.getApellidos())
                .tipoDocumento(familiar.getTipoDocumento())
                .numeroDocumento(familiar.getNumeroDocumento())
                .parentesco(familiar.getParentesco())
                .activo(familiar.isActivo())
                .createdAt(familiar.getCreatedAt())
                .updatedAt(familiar.getUpdatedAt())
                .build();
    }
}
