package com.example.msmarkets.infrastructure.persistence;

import com.example.msmarkets.domain.model.Market;
import com.example.msmarkets.application.port.out.MarketRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia: traduce entre el modelo de dominio Market
 * y la entidad JPA, e implementa el puerto de salida que pide el dominio.
 */
@Component
@RequiredArgsConstructor
class MarketRepositoryAdapter implements MarketRepositoryPort {

    private final SpringDataMarketRepository jpaRepository;

    @Override
    public List<Market> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Market> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByMarcaAndTipo(String marca, String tipo) {
        return jpaRepository.existsByMarcaAndTipo(marca, tipo);
    }

    @Override
    public Market save(Market market) {
        MarketJpaEntity entity = toEntity(market);
        MarketJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    private Market toDomain(MarketJpaEntity entity) {
        return Market.builder()
                .id(entity.getId())
                .marca(entity.getMarca())
                .tipo(entity.getTipo())
                .slaEntregaHoras(entity.getSlaEntregaHoras())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private MarketJpaEntity toEntity(Market market) {
        return MarketJpaEntity.builder()
                .id(market.getId())
                .marca(market.getMarca())
                .tipo(market.getTipo())
                .slaEntregaHoras(market.getSlaEntregaHoras())
                .active(market.isActive())
                .createdAt(market.getCreatedAt())
                .updatedAt(market.getUpdatedAt())
                .build();
    }
}
