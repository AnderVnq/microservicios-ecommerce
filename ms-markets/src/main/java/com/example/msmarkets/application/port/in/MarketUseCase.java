package com.example.msmarkets.application.port.in;

import com.example.msmarkets.domain.model.Market;

import java.util.List;

/**
 * Puerto de entrada: operaciones de negocio disponibles para cualquier
 * adaptador driver (REST, mensajeria, etc.).
 */
public interface MarketUseCase {

    List<Market> findAll();

    Market findById(Long id);

    Market create(String marca, String tipo, Integer slaEntregaHoras);

    Market update(Long id, String marca, String tipo, Integer slaEntregaHoras);

    void deactivate(Long id);
}
