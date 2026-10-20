package com.campuslab.bookings.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * Configuracion de mensajeria RabbitMQ del lado productor (bookings). La
 * logica de negocio (ReservaServiceImpl) no conoce exchanges ni routing
 * keys: solo llama a ReservaEventPublisher, que los toma de
 * RabbitMqProperties.
 *
 * <pre>
 *   bookings --email.send---> cmd.direct --> q.cmd.email   (notify)
 *   bookings --prep.ticket--> cmd.direct --> q.cmd.prep    (notify)
 *   bookings --voucher.gen--> cmd.direct --> q.cmd.voucher (notify)
 * </pre>
 *
 * Las colas, sus DLQ y los bindings los declara el consumidor
 * (ms-campuslab-notify); aqui solo se declara el exchange al que publica
 * este servicio.
 */
@Configuration
@EnableConfigurationProperties(RabbitMqProperties.class)
public class RabbitMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);

    @Bean
    public DirectExchange commandExchange(RabbitMqProperties props) {
        return ExchangeBuilder.directExchange(props.exchange()).durable(true).build();
    }

    /**
     * Mensajes no entregados del lado productor: con mandatory=true
     * (spring.rabbitmq.template.mandatory) el broker devuelve los mensajes
     * que no coinciden con ningun binding, en vez de descartarlos en
     * silencio. Se registran en el log con su routing key para poder
     * diagnosticar un binding faltante.
     */
    @Bean
    public RabbitTemplateCustomizer mensajesNoEnrutadosCustomizer() {
        return template -> template.setReturnsCallback(returned -> log.error(
                "Mensaje NO entregado por RabbitMQ: exchange={} routingKey={} replyCode={} replyText={} cuerpo={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText(),
                new String(returned.getMessage().getBody(), StandardCharsets.UTF_8)));
    }
}
