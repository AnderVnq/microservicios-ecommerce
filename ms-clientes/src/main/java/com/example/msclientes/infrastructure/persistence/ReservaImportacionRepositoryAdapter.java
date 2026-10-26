package com.example.msclientes.infrastructure.persistence;

import com.example.msclientes.domain.model.ReservaImportacion;
import com.example.msclientes.application.port.out.ReservaImportacionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class ReservaImportacionRepositoryAdapter implements ReservaImportacionRepositoryPort {

    private final SpringDataReservaImportacionRepository jpaRepository;

    @Override
    public Optional<ReservaImportacion> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public ReservaImportacion save(ReservaImportacion reserva) {
        ReservaImportacionJpaEntity saved = jpaRepository.save(toEntity(reserva));
        return toDomain(saved);
    }

    private ReservaImportacion toDomain(ReservaImportacionJpaEntity entity) {
        return ReservaImportacion.builder()
                .id(entity.getId())
                .clienteId(entity.getClienteId())
                .pedidoId(entity.getPedidoId())
                .idempotencyKey(entity.getIdempotencyKey())
                .estado(entity.getEstado())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private ReservaImportacionJpaEntity toEntity(ReservaImportacion reserva) {
        return ReservaImportacionJpaEntity.builder()
                .id(reserva.getId())
                .clienteId(reserva.getClienteId())
                .pedidoId(reserva.getPedidoId())
                .idempotencyKey(reserva.getIdempotencyKey())
                .estado(reserva.getEstado())
                .createdAt(reserva.getCreatedAt())
                .build();
    }
}
