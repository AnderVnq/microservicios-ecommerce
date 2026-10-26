package com.example.msmarkets.application.port.out;

import com.example.msmarkets.domain.model.Market;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida: lo que el dominio necesita para persistir Market,
 * implementado por un adaptador en infrastructure.persistence.
 */
public interface MarketRepositoryPort {

    List<Market> findAll();

    Optional<Market> findById(Long id);

    boolean existsByCode(String code);

    Market save(Market market);
}
