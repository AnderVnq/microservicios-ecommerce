package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.domain.model.Pedido;
import com.example.mspedidos.application.port.out.PedidoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class PedidoRepositoryAdapter implements PedidoRepositoryPort {

    private final SpringDataPedidoRepository jpaRepository;

    @Override
    public List<Pedido> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Pedido> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Pedido save(Pedido pedido) {
        PedidoJpaEntity saved = jpaRepository.save(toEntity(pedido));
        return toDomain(saved);
    }

    private Pedido toDomain(PedidoJpaEntity entity) {
        return Pedido.builder()
                .id(entity.getId())
                .marketId(entity.getMarketId())
                .clienteId(entity.getClienteId())
                .familiarId(entity.getFamiliarId())
                .requiereImportacion(entity.isRequiereImportacion())
                .estado(entity.getEstado())
                .total(entity.getTotal())
                .moneda(entity.getMoneda())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PedidoJpaEntity toEntity(Pedido pedido) {
        return PedidoJpaEntity.builder()
                .id(pedido.getId())
                .marketId(pedido.getMarketId())
                .clienteId(pedido.getClienteId())
                .familiarId(pedido.getFamiliarId())
                .requiereImportacion(pedido.isRequiereImportacion())
                .estado(pedido.getEstado())
                .total(pedido.getTotal())
                .moneda(pedido.getMoneda())
                .createdAt(pedido.getCreatedAt())
                .updatedAt(pedido.getUpdatedAt())
                .build();
    }
}
