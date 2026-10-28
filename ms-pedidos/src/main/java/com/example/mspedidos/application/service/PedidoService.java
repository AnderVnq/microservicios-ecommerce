package com.example.mspedidos.application.service;

import com.example.mspedidos.domain.exception.PedidoNotFoundException;
import com.example.mspedidos.domain.model.EstadoPedido;
import com.example.mspedidos.domain.model.Pedido;
import com.example.mspedidos.application.port.in.PedidoUseCase;
import com.example.mspedidos.application.port.out.PedidoEventPublisherPort;
import com.example.mspedidos.application.port.out.PedidoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Saga coreografiado para el cupo de importaciones: ms-pedidos nunca llama
 * sincronamente a ms-clientes. Publica su intencion via PedidoEventPublisherPort
 * y el pedido queda PENDIENTE hasta que onRespuestaReservaCupo (disparado por
 * el listener de RabbitMQ) confirma o cancela el pedido.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PedidoService implements PedidoUseCase {

    private final PedidoRepositoryPort pedidoRepository;
    private final PedidoEventPublisherPort pedidoEventPublisher;

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
            // El pedido queda PENDIENTE: el saga lo confirma o cancela de forma asincrona.
            pedidoEventPublisher.publicarSolicitudReservaCupo(pedido.getId(), clienteId, pedido.idempotencyKeyCupo());
        } else {
            pedido.confirmar();
            pedido = pedidoRepository.save(pedido);
        }
        return pedido;
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
        boolean compensarCupo = pedido.isRequiereImportacion() && pedido.getEstado() == EstadoPedido.CONFIRMADO;
        pedido.cancelar();
        pedidoRepository.save(pedido);

        if (compensarCupo) {
            // Accion compensatoria del saga: el cupo ya estaba reservado, hay que liberarlo.
            pedidoEventPublisher.publicarPedidoCancelado(pedido.getId(), pedido.getClienteId(), pedido.idempotencyKeyCupo());
        }
    }

    @Override
    @Transactional
    public void onRespuestaReservaCupo(Long pedidoId, boolean cupoReservado) {
        Pedido pedido = findById(pedidoId);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            return; // Respuesta tardia o duplicada del saga: el pedido ya fue resuelto.
        }
        if (cupoReservado) {
            pedido.confirmar();
        } else {
            pedido.cancelar();
        }
        pedidoRepository.save(pedido);
    }
}
