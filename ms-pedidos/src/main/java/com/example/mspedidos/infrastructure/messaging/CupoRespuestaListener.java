package com.example.mspedidos.infrastructure.messaging;

import com.example.mspedidos.application.port.in.PedidoUseCase;
import com.example.mspedidos.infrastructure.messaging.event.CupoReservaRespuestaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Paso final del saga: aplica el resultado de la reserva de cupo sobre el pedido. */
@Component
@RequiredArgsConstructor
@Slf4j
class CupoRespuestaListener {

    private final PedidoUseCase pedidoUseCase;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_RESPUESTAS_CUPO)
    public void onCupoRespuesta(CupoReservaRespuestaEvent evento) {
        log.info("Respuesta de cupo para pedido {}: exito={} motivo={}",
                evento.pedidoId(), evento.exito(), evento.motivo());
        pedidoUseCase.onRespuestaReservaCupo(evento.pedidoId(), evento.exito());
    }
}
