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

    Market create(String code, String name, String currency);

    Market update(Long id, String code, String name, String currency);

    void deactivate(Long id);
}
