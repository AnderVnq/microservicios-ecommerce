package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderHeaderChanges;

import java.util.List;

public interface OrderUseCase {

    OrderView create(CreateOrderCommand command);

    /** Todas las ordenes con sus unidades. */
    List<OrderView> findAll();

    OrderView findById(Long id);

    /** Actualiza solo la cabecera; no toca las unidades. */
    Order updateHeader(Long id, OrderHeaderChanges changes);

    /** Cancela todas las unidades no terminales de la orden y recalcula el total. */
    OrderView cancel(Long id);
}
