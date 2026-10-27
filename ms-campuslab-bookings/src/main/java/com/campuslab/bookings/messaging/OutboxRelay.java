package com.campuslab.bookings.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Envia a RabbitMQ los mensajes PENDIENTE del outbox, cada uno con
 * confirmacion del broker (publisher confirms) y mandatory=true:
 *
 * <ul>
 *   <li>ack y enrutado        -> ENVIADO.</li>
 *   <li>broker caido / timeout -> sigue PENDIENTE y se corta la pasada (se
 *       reintenta en la siguiente, sin perder el orden).</li>
 *   <li>nack o sin binding (exchange/cola borrados) -> sigue PENDIENTE con
 *       el error registrado; al llegar a max-intentos pasa a FALLIDO y queda
 *       en log.error para revisarlo.</li>
 * </ul>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "campuslab.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final MensajeOutboxRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final int maxIntentos;
    private final long timeoutConfirmacionMs;
    private final int lote;

    public OutboxRelay(MensajeOutboxRepository outbox,
                       RabbitTemplate rabbitTemplate,
                       @Value("${campuslab.outbox.relay.max-intentos:20}") int maxIntentos,
                       @Value("${campuslab.outbox.relay.timeout-confirmacion-ms:5000}") long timeoutConfirmacionMs,
                       @Value("${campuslab.outbox.relay.lote:50}") int lote) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.maxIntentos = maxIntentos;
        this.timeoutConfirmacionMs = timeoutConfirmacionMs;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${campuslab.outbox.relay.intervalo-ms:5000}")
    public void enviarPendientes() {
        List<MensajeOutbox> pendientes = outbox.findByEstadoOrderByIdAsc(
                MensajeOutbox.Estado.PENDIENTE, PageRequest.of(0, lote));

        for (MensajeOutbox mensaje : pendientes) {
            Resultado resultado = enviar(mensaje);
            outbox.save(mensaje);
            if (resultado == Resultado.BROKER_NO_DISPONIBLE) {
                log.warn("RabbitMQ no disponible: {} mensajes quedan pendientes en el outbox para la proxima pasada.",
                        pendientes.size());
                return;
            }
        }
    }

    /** Borra los ya enviados hace mas de 7 dias (todos los dias a las 03:30). */
    @Scheduled(cron = "${campuslab.outbox.relay.limpieza-cron:0 30 3 * * *}")
    public void limpiarEnviados() {
        outbox.deleteByEstadoAndEnviadoEnBefore(MensajeOutbox.Estado.ENVIADO, LocalDateTime.now().minusDays(7));
    }

    enum Resultado { ENVIADO, RECHAZADO, BROKER_NO_DISPONIBLE }

    Resultado enviar(MensajeOutbox mensaje) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propiedades.setMessageId(mensaje.getEventId());
        propiedades.setType(mensaje.getTipo());
        Message amqp = new Message(mensaje.getCuerpo().getBytes(StandardCharsets.UTF_8), propiedades);
        CorrelationData correlacion = new CorrelationData(mensaje.getEventId());

        try {
            rabbitTemplate.send(mensaje.getExchange(), mensaje.getRoutingKey(), amqp, correlacion);
            CorrelationData.Confirm confirmacion = correlacion.getFuture().get(timeoutConfirmacionMs, TimeUnit.MILLISECONDS);

            if (!confirmacion.isAck()) {
                return rechazado(mensaje, "nack del broker: " + confirmacion.getReason());
            }
            if (correlacion.getReturned() != null) {
                return rechazado(mensaje, "sin binding para exchange=" + mensaje.getExchange()
                        + " routingKey=" + mensaje.getRoutingKey() + " (" + correlacion.getReturned().getReplyText() + ")");
            }
            mensaje.setEstado(MensajeOutbox.Estado.ENVIADO);
            mensaje.setEnviadoEn(LocalDateTime.now());
            log.info("Evento {} enviado a RabbitMQ (exchange={}, routingKey={}, eventId={})",
                    mensaje.getTipo(), mensaje.getExchange(), mensaje.getRoutingKey(), mensaje.getEventId());
            return Resultado.ENVIADO;
        } catch (AmqpConnectException | TimeoutException ex) {
            mensaje.registrarFallo("RabbitMQ no disponible: " + ex.getMessage());
            return Resultado.BROKER_NO_DISPONIBLE;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            mensaje.registrarFallo("envio interrumpido");
            return Resultado.BROKER_NO_DISPONIBLE;
        } catch (AmqpException | ExecutionException ex) {
            return rechazado(mensaje, ex.getMessage());
        }
    }

    private Resultado rechazado(MensajeOutbox mensaje, String motivo) {
        mensaje.registrarFallo(motivo);
        if (mensaje.getIntentos() >= maxIntentos) {
            mensaje.setEstado(MensajeOutbox.Estado.FALLIDO);
            log.error("Evento {} (eventId={}) marcado FALLIDO tras {} intentos: {}. Cuerpo: {}",
                    mensaje.getTipo(), mensaje.getEventId(), mensaje.getIntentos(), motivo, mensaje.getCuerpo());
        } else {
            log.warn("Evento {} (eventId={}) no entregado (intento {}/{}): {}",
                    mensaje.getTipo(), mensaje.getEventId(), mensaje.getIntentos(), maxIntentos, motivo);
        }
        return Resultado.RECHAZADO;
    }
}
