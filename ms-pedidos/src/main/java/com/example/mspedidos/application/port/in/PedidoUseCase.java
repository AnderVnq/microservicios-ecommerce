package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.EstadoPedido;
import com.example.mspedidos.domain.model.Pedido;

import java.math.BigDecimal;
import java.util.List;

public interface PedidoUseCase {

    List<Pedido> findAll();

    Pedido findById(Long id);

    /**
     * clienteId es siempre el titular. familiarId es opcional: quien hizo
     * fisicamente el pedido si fue un familiar asociado (comparte el cupo del titular).
     */
    Pedido crear(Long marketId, Long clienteId, Long familiarId, boolean requiereImportacion,
                 BigDecimal total, String moneda);

    Pedido cambiarEstado(Long id, EstadoPedido nuevoEstado);

    void cancelar(Long id);
}
