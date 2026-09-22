package com.campuslab.bookings.messaging;

import com.campuslab.bookings.model.Reserva;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publica a Kafka (topico bookings.events) el historial completo del ciclo
 * de vida de una reserva: es la fuente de verdad que consumen
 * ms-campuslab-audit (timeline quien/que/cuando) y ms-campuslab-report
 * (KPIs). A diferencia de ReservaEventPublisher (RabbitMQ, que solo publica
 * las transiciones que disparan una notificacion), este metodo se llama en
 * CADA cambio de estado, incluida la creacion.
 *
 * Se usa el id de la reserva como key del mensaje para que Kafka garantice
 * el orden de los eventos de una misma reserva (van todos a la misma
 * particion). KafkaTemplate.send es asincrono: un fallo del broker no
 * bloquea ni revierte la transaccion de negocio, solo se registra el error
 * cuando el future se completa.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaKafkaEventPublisher {

    private static final String TOPIC = "bookings.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publicar(String type, Reserva reserva) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservaId", reserva.getId());
        payload.put("recursoId", reserva.getRecursoId());
        payload.put("usuarioSolicitanteId", reserva.getUsuarioSolicitanteId());
        payload.put("estado", reserva.getEstado().name());
        payload.put("fechaInicio", reserva.getFechaInicio());
        payload.put("fechaFin", reserva.getFechaFin());
        payload.put("aprobadoPor", reserva.getAprobadoPor());
        payload.put("fechaAprobacion", reserva.getFechaAprobacion());
        payload.put("motivoCancelacion", reserva.getMotivoCancelacion());

        String key = String.valueOf(reserva.getId());
        EventEnvelope envelope = EventEnvelope.of(type, "reserva-" + reserva.getId(), payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(TOPIC, key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("No fue posible publicar el evento {} de la reserva {} en bookings.events",
                            type, reserva.getId(), ex);
                } else {
                    log.info("Evento {} publicado en bookings.events (reservaId={}, eventId={})",
                            type, reserva.getId(), envelope.eventId());
                }
            });
        } catch (JsonProcessingException ex) {
            log.error("No fue posible serializar el evento {} de la reserva {}", type, reserva.getId(), ex);
        }
    }
}
