package com.campuslab.bookings.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private MensajeOutboxRepository outbox;
    private RabbitTemplate rabbitTemplate;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        outbox = mock(MensajeOutboxRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        relay = new OutboxRelay(outbox, rabbitTemplate, 3, 200, 50);
    }

    @Test
    void brokerConfirma_mensajeQuedaEnviado() {
        MensajeOutbox m = pendiente("e1");
        responder(cd -> cd.getFuture().complete(new CorrelationData.Confirm(true, null)));

        assertThat(relay.enviar(m)).isEqualTo(OutboxRelay.Resultado.ENVIADO);
        assertThat(m.getEstado()).isEqualTo(MensajeOutbox.Estado.ENVIADO);
        assertThat(m.getEnviadoEn()).isNotNull();
    }

    @Test
    void brokerCaido_quedaPendienteYSeCortaLaPasadaSinPerderElOrden() {
        MensajeOutbox m1 = pendiente("e1");
        MensajeOutbox m2 = pendiente("e2");
        when(outbox.findByEstadoOrderByIdAsc(any(), any())).thenReturn(List.of(m1, m2));
        doThrow(new AmqpConnectException(new RuntimeException("Connection refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.enviarPendientes();

        assertThat(m1.getEstado()).isEqualTo(MensajeOutbox.Estado.PENDIENTE);
        assertThat(m1.getIntentos()).isEqualTo(1);
        assertThat(m2.getIntentos()).isZero(); // no se intento: espera la siguiente pasada
        verify(rabbitTemplate, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void brokerNoConfirmaATiempo_quedaPendiente() {
        MensajeOutbox m = pendiente("e1");
        responder(cd -> { }); // nunca llega el ack

        assertThat(relay.enviar(m)).isEqualTo(OutboxRelay.Resultado.BROKER_NO_DISPONIBLE);
        assertThat(m.getEstado()).isEqualTo(MensajeOutbox.Estado.PENDIENTE);
    }

    @Test
    void exchangeBorrado_nack_seReintentaYAlLimitePasaAFallido() {
        MensajeOutbox m = pendiente("e1");
        responder(cd -> cd.getFuture().complete(new CorrelationData.Confirm(false, "channel error; NOT_FOUND - no exchange 'cmd.direct'")));

        relay.enviar(m);
        relay.enviar(m);
        assertThat(m.getEstado()).isEqualTo(MensajeOutbox.Estado.PENDIENTE);
        relay.enviar(m);

        assertThat(m.getEstado()).isEqualTo(MensajeOutbox.Estado.FALLIDO);
        assertThat(m.getUltimoError()).contains("NOT_FOUND");
    }

    @Test
    void colaSinBinding_mensajeDevuelto_noSeMarcaComoEnviado() {
        MensajeOutbox m = pendiente("e1");
        responder(cd -> {
            cd.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE", "cmd.direct", "prep.ticket"));
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
        });

        assertThat(relay.enviar(m)).isEqualTo(OutboxRelay.Resultado.RECHAZADO);
        assertThat(m.getEstado()).isEqualTo(MensajeOutbox.Estado.PENDIENTE);
        assertThat(m.getUltimoError()).contains("NO_ROUTE");
    }

    @Test
    void unMensajeRechazado_noBloqueaALosSiguientes() {
        MensajeOutbox malo = pendiente("malo");
        MensajeOutbox bueno = pendiente("bueno");
        when(outbox.findByEstadoOrderByIdAsc(any(), any())).thenReturn(List.of(malo, bueno));
        responder(cd -> cd.getFuture().complete(new CorrelationData.Confirm(!"malo".equals(cd.getId()), "nack")));

        relay.enviarPendientes();

        assertThat(malo.getEstado()).isEqualTo(MensajeOutbox.Estado.PENDIENTE);
        assertThat(bueno.getEstado()).isEqualTo(MensajeOutbox.Estado.ENVIADO);
        verify(outbox, never()).delete(any());
    }

    private void responder(Consumer<CorrelationData> broker) {
        doAnswer(inv -> {
            broker.accept(inv.getArgument(3));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private static MensajeOutbox pendiente(String eventId) {
        return MensajeOutbox.builder().eventId(eventId).tipo("RESERVA_APROBADA").exchange("cmd.direct")
                .routingKey("email.send").cuerpo("{\"eventId\":\"" + eventId + "\"}")
                .estado(MensajeOutbox.Estado.PENDIENTE).build();
    }
}
