package com.campuslab.bookings.messaging;

import com.campuslab.bookings.config.RabbitMqProperties;
import com.campuslab.bookings.model.Reserva;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publica a RabbitMQ los eventos de cambio de estado de una reserva, para
 * que ms-campuslab-notify dispare el email/ticket/voucher correspondiente.
 * El exchange y las routing keys vienen de application.yml
 * (campuslab.rabbitmq.*, ver RabbitMqProperties / RabbitMqConfig).
 *
 * Mapeo evento -> routing key:
 *   APROBADA        -> routing-keys.email   (aviso de aprobacion al estudiante)
 *   EN_PREPARACION  -> routing-keys.prep    (ticket de preparacion para el tecnico)
 *   EN_USO          -> routing-keys.voucher (voucher/comprobante de retiro)
 *   CANCELADA       -> routing-keys.email   (aviso de cancelacion)
 *   DEVUELTA        -> routing-keys.email   (confirmacion de devolucion)
 *
 * Es best-effort: si el broker no esta disponible, se registra el error pero
 * no se interrumpe la transaccion de negocio (la reserva igual cambia de
 * estado; la notificacion se pierde en vez de bloquear al usuario).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitMqProperties props;

    public void publicarAprobada(Reserva reserva) {
        publicar(props.routingKeys().email(), "RESERVA_APROBADA", reserva);
    }

    public void publicarEnPreparacion(Reserva reserva) {
        publicar(props.routingKeys().prep(), "RESERVA_EN_PREPARACION", reserva);
    }

    public void publicarEnUso(Reserva reserva) {
        publicar(props.routingKeys().voucher(), "RESERVA_EN_USO", reserva);
    }

    public void publicarCancelada(Reserva reserva) {
        publicar(props.routingKeys().email(), "RESERVA_CANCELADA", reserva);
    }

    public void publicarDevuelta(Reserva reserva) {
        publicar(props.routingKeys().email(), "RESERVA_DEVUELTA", reserva);
    }

    private void publicar(String routingKey, String type, Reserva reserva) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservaId", reserva.getId());
        payload.put("recursoId", reserva.getRecursoId());
        payload.put("usuarioSolicitanteId", reserva.getUsuarioSolicitanteId());
        payload.put("estado", reserva.getEstado().name());

        EventEnvelope envelope = EventEnvelope.of(type, "reserva-" + reserva.getId(), payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            rabbitTemplate.convertAndSend(props.exchange(), routingKey, json);
            log.info("Evento {} publicado (exchange={}, routingKey={}, reservaId={}, eventId={})",
                    type, props.exchange(), routingKey, reserva.getId(), envelope.eventId());
        } catch (JsonProcessingException ex) {
            log.error("No fue posible serializar el evento {} de la reserva {}", type, reserva.getId(), ex);
        } catch (AmqpException ex) {
            log.error("No fue posible publicar el evento {} de la reserva {}: RabbitMQ no disponible",
                    type, reserva.getId(), ex);
        }
    }
}
