package com.campuslab.bookings.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre comun de evento publicado por bookings hacia RabbitMQ, con el mismo
 * formato que ms-campuslab-notify espera consumir (type, eventId, timestamp,
 * traceId, correlationId, payload).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        Map<String, Object> payload
) {

    public static EventEnvelope of(String type, String correlationId, Map<String, Object> payload) {
        return new EventEnvelope(
                type,
                UUID.randomUUID().toString(),
                Instant.now(),
                UUID.randomUUID().toString(),
                correlationId,
                payload
        );
    }
}
