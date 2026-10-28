package com.example.msclientes.infrastructure.messaging;

import com.example.msclientes.application.port.in.CupoImportacionUseCase;
import com.example.msclientes.infrastructure.messaging.event.PedidoCanceladoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Compensacion del saga: libera el cupo que se habia reservado para el pedido cancelado. */
@Component
@RequiredArgsConstructor
@Slf4j
class PedidoCanceladoListener {

    private final CupoImportacionUseCase cupoImportacionUseCase;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PEDIDO_CANCELADO)
    public void onPedidoCancelado(PedidoCanceladoEvent evento) {
        log.info("Liberando cupo del pedido cancelado {} (cliente {})", evento.pedidoId(), evento.clienteId());
        cupoImportacionUseCase.liberar(evento.idempotencyKey());
    }
}
