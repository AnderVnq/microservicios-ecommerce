package com.example.mspedidos.infrastructure.messaging;

import com.example.mspedidos.application.port.out.PedidoEventPublisherPort;
import com.example.mspedidos.infrastructure.messaging.event.PedidoCanceladoEvent;
import com.example.mspedidos.infrastructure.messaging.event.SolicitudReservaCupoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PedidoEventPublisherAdapter implements PedidoEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publicarSolicitudReservaCupo(Long pedidoId, Long clienteId, String idempotencyKey) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.SAGA_EXCHANGE, RabbitMQConfig.ROUTING_KEY_SOLICITUD_CUPO,
                new SolicitudReservaCupoEvent(pedidoId, clienteId, idempotencyKey));
    }

    @Override
    public void publicarPedidoCancelado(Long pedidoId, Long clienteId, String idempotencyKey) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.SAGA_EXCHANGE, RabbitMQConfig.ROUTING_KEY_PEDIDO_CANCELADO,
                new PedidoCanceladoEvent(pedidoId, clienteId, idempotencyKey));
    }
}
