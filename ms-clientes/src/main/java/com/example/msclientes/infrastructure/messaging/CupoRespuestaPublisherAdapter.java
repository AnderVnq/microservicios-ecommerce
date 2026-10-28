package com.example.msclientes.infrastructure.messaging;

import com.example.msclientes.application.port.out.CupoRespuestaPublisherPort;
import com.example.msclientes.infrastructure.messaging.event.CupoReservaRespuestaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CupoRespuestaPublisherAdapter implements CupoRespuestaPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publicarRespuesta(Long pedidoId, Long clienteId, String idempotencyKey, boolean exito, String motivo) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.SAGA_EXCHANGE, RabbitMQConfig.ROUTING_KEY_RESPUESTA_CUPO,
                new CupoReservaRespuestaEvent(pedidoId, clienteId, idempotencyKey, exito, motivo));
    }
}
