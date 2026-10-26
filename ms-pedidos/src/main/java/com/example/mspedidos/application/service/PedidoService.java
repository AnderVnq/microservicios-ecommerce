package com.example.mspedidos.application.service;

import com.example.mspedidos.domain.exception.CupoImportacionNoDisponibleException;
import com.example.mspedidos.domain.exception.PedidoNotFoundException;
import com.example.mspedidos.domain.model.EstadoPedido;
import com.example.mspedidos.domain.model.Pedido;
import com.example.mspedidos.application.port.in.PedidoUseCase;
import com.example.mspedidos.application.port.out.CupoImportacionPort;
import com.example.mspedidos.application.port.out.PedidoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PedidoService implements PedidoUseCase {

    private final PedidoRepositoryPort pedidoRepository;
    private final CupoImportacionPort cupoImportacionPort;

    @Override
    public List<Pedido> findAll() {
        return pedidoRepository.findAll();
    }

    @Override
    public Pedido findById(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));
    }

    @Override
    @Transactional
    public Pedido crear(Long marketId, Long clienteId, Long familiarId, boolean requiereImportacion,
                         BigDecimal total, String moneda) {
        Pedido pedido = pedidoRepository.save(
                Pedido.nuevo(marketId, clienteId, familiarId, requiereImportacion, total, moneda));

        if (requiereImportacion) {
            try {
                cupoImportacionPort.reservar(clienteId, pedido.getId(), pedido.idempotencyKeyCupo());
                pedido.confirmar();
            } catch (CupoImportacionNoDisponibleException e) {
                pedido.cancelar();
                pedidoRepository.save(pedido);
                throw e;
            }
        } else {
            pedido.confirmar();
        }
        return pedidoRepository.save(pedido);
    }

    @Override
    @Transactional
    public Pedido cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = findById(id);
        pedido.cambiarEstado(nuevoEstado);
        return pedidoRepository.save(pedido);
    }

    @Override
    @Transactional
    public void cancelar(Long id) {
        Pedido pedido = findById(id);
        if (pedido.isRequiereImportacion() && pedido.getEstado() != EstadoPedido.CANCELADO) {
            cupoImportacionPort.liberar(pedido.getClienteId(), pedido.idempotencyKeyCupo());
        }
        pedido.cancelar();
        pedidoRepository.save(pedido);
    }
}
