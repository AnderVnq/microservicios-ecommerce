package com.example.mspedidos.infrastructure.messaging;

import com.example.mspedidos.infrastructure.messaging.event.CupoReservaRespuestaEvent;
import com.example.mspedidos.infrastructure.messaging.event.PedidoCanceladoEvent;
import com.example.mspedidos.infrastructure.messaging.event.SolicitudReservaCupoEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

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

    /**
     * Mapea el __TypeId__ a un id logico compartido en vez del nombre de clase
     * completo, porque ms-pedidos y ms-clientes declaran el mismo evento en
     * paquetes distintos (cada uno dueno de su propio contrato de mensajeria).
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTrustedPackages("com.example.mspedidos.infrastructure.messaging.event");
        typeMapper.setIdClassMapping(Map.of(
                "SolicitudReservaCupoEvent", SolicitudReservaCupoEvent.class,
                "CupoReservaRespuestaEvent", CupoReservaRespuestaEvent.class,
                "PedidoCanceladoEvent", PedidoCanceladoEvent.class));

        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
