package com.example.msmarkets.application.service;

import com.example.msmarkets.domain.exception.MarketAlreadyExistsException;
import com.example.msmarkets.domain.exception.MarketNotFoundException;
import com.example.msmarkets.domain.model.Market;
import com.example.msmarkets.application.port.in.MarketUseCase;
import com.example.msmarkets.application.port.out.MarketRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orquesta el caso de uso apoyandose unicamente en el puerto de salida:
 * no conoce JPA, HTTP ni ningun detalle de infraestructura.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketService implements MarketUseCase {

    private final MarketRepositoryPort marketRepository;

    @Override
    public List<Market> findAll() {
        return marketRepository.findAll();
    }

    @Override
    public Market findById(Long id) {
        return marketRepository.findById(id)
                .orElseThrow(() -> new MarketNotFoundException(id));
    }

    @Override
    @Transactional
    public Market create(String marca, String tipo, Integer slaEntregaHoras) {
        if (marketRepository.existsByMarcaAndTipo(marca, tipo)) {
            throw new MarketAlreadyExistsException(marca, tipo);
        }
        return marketRepository.save(Market.createNew(marca, tipo, slaEntregaHoras));
    }

    @Override
    @Transactional
    public Market update(Long id, String marca, String tipo, Integer slaEntregaHoras) {
        Market market = findById(id);
        market.actualizar(marca, tipo, slaEntregaHoras);
        return marketRepository.save(market);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Market market = findById(id);
        market.deactivate();
        marketRepository.save(market);
    }
}
