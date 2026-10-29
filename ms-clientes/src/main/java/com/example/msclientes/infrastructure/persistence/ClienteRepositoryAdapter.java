package com.example.msclientes.infrastructure.persistence;

import com.example.msclientes.domain.model.Cliente;
import com.example.msclientes.application.port.out.ClienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class ClienteRepositoryAdapter implements ClienteRepositoryPort {

    private final SpringDataClienteRepository jpaRepository;

    @Override
    public List<Cliente> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Cliente> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByDocumento(String tipoDocumento, String numeroDocumento) {
        return jpaRepository.existsByTipoDocumentoAndNumeroDocumento(tipoDocumento, numeroDocumento);
    }

    @Override
    public Cliente save(Cliente cliente) {
        ClienteJpaEntity saved = jpaRepository.save(toEntity(cliente));
        return toDomain(saved);
    }

    private Cliente toDomain(ClienteJpaEntity entity) {
        return Cliente.builder()
                .id(entity.getId())
                .nombres(entity.getNombres())
                .apellidos(entity.getApellidos())
                .tipoDocumento(entity.getTipoDocumento())
                .numeroDocumento(entity.getNumeroDocumento())
                .email(entity.getEmail())
                .telefono(entity.getTelefono())
                .activo(entity.isActivo())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ClienteJpaEntity toEntity(Cliente cliente) {
        return ClienteJpaEntity.builder()
                .id(cliente.getId())
                .nombres(cliente.getNombres())
                .apellidos(cliente.getApellidos())
                .tipoDocumento(cliente.getTipoDocumento())
                .numeroDocumento(cliente.getNumeroDocumento())
                .email(cliente.getEmail())
                .telefono(cliente.getTelefono())
                .activo(cliente.isActivo())
                .createdAt(cliente.getCreatedAt())
                .updatedAt(cliente.getUpdatedAt())
                .build();
    }
}
