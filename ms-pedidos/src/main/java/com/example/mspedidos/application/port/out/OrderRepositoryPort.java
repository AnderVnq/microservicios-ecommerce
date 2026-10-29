package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(Long id);

    /** Como {@link #findById} pero con lock pesimista de escritura; requiere transaccion. */
    Optional<Order> findByIdForUpdate(Long id);

    List<Order> findAll();

    boolean existsByMarketIdAndExternalOrderId(Long marketId, String externalOrderId);
}
