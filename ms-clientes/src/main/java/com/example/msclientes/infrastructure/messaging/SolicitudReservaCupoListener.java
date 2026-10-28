package com.example.msclientes.infrastructure.messaging;

import com.example.msclientes.application.port.in.CupoImportacionUseCase;
import com.example.msclientes.application.port.out.CupoRespuestaPublisherPort;
import com.example.msclientes.domain.exception.CupoImportacionAgotadoException;
import com.example.msclientes.infrastructure.messaging.event.SolicitudReservaCupoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Participante del saga: intenta reservar el cupo del cliente titular y
 * siempre responde (exito o fallo) para que ms-pedidos pueda resolver el pedido.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SolicitudReservaCupoListener {

    private final CupoImportacionUseCase cupoImportacionUseCase;
    private final CupoRespuestaPublisherPort cupoRespuestaPublisher;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SOLICITUD_CUPO)
    public void onSolicitudReservaCupo(SolicitudReservaCupoEvent evento) {
        try {
            cupoImportacionUseCase.reservar(evento.clienteId(), evento.pedidoId(), evento.idempotencyKey());
            cupoRespuestaPublisher.publicarRespuesta(evento.pedidoId(), evento.clienteId(), evento.idempotencyKey(), true, null);
        } catch (CupoImportacionAgotadoException e) {
            log.info("Cupo agotado para cliente {} (pedido {})", evento.clienteId(), evento.pedidoId());
            cupoRespuestaPublisher.publicarRespuesta(evento.pedidoId(), evento.clienteId(), evento.idempotencyKey(), false, e.getMessage());
        }
    }
}
