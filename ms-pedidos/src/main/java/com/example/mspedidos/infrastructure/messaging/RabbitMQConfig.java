package com.example.mspedidos.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia del saga de pedidos (coreografiado via eventos). El exchange y
 * los routing keys son el "contrato" compartido entre ms-pedidos y
 * ms-clientes; cada servicio solo declara las colas que el mismo consume.
 */
@Configuration
public class RabbitMQConfig {

    public static final String SAGA_EXCHANGE = "saga.pedidos.exchange";

    public static final String ROUTING_KEY_SOLICITUD_CUPO = "pedido.importacion.solicitado";
    public static final String ROUTING_KEY_RESPUESTA_CUPO = "cupo.reserva.respuesta";
    public static final String ROUTING_KEY_PEDIDO_CANCELADO = "pedido.cancelado";

    public static final String QUEUE_RESPUESTAS_CUPO = "pedidos.cupo.respuestas.queue";

    @Bean
    TopicExchange sagaExchange() {
        return new TopicExchange(SAGA_EXCHANGE, true, false);
    }

    @Bean
    Queue respuestasCupoQueue() {
        return new Queue(QUEUE_RESPUESTAS_CUPO, true);
    }

    @Bean
    Binding respuestasCupoBinding(Queue respuestasCupoQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(respuestasCupoQueue).to(sagaExchange).with(ROUTING_KEY_RESPUESTA_CUPO);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
