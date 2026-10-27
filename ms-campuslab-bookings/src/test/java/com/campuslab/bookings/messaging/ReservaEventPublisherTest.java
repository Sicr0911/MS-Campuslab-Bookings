package com.campuslab.bookings.messaging;

import com.campuslab.bookings.config.RabbitMqProperties;
import com.campuslab.bookings.model.EstadoReserva;
import com.campuslab.bookings.model.Reserva;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReservaEventPublisherTest {

    private MensajeOutboxRepository outbox;
    private ReservaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        outbox = mock(MensajeOutboxRepository.class);
        RabbitMqProperties props = new RabbitMqProperties("x.prueba",
                new RabbitMqProperties.RoutingKeys("rk.email", "rk.prep", "rk.voucher"));
        publisher = new ReservaEventPublisher(outbox, new ObjectMapper().findAndRegisterModules(), props);
    }

    @Test
    void aprobada_quedaEnElOutboxConExchangeYRoutingKeyDeEmail() {
        MensajeOutbox m = guardado(() -> publisher.publicarAprobada(reserva(EstadoReserva.APROBADA)));

        assertThat(m.getExchange()).isEqualTo("x.prueba");
        assertThat(m.getRoutingKey()).isEqualTo("rk.email");
        assertThat(m.getTipo()).isEqualTo("RESERVA_APROBADA");
        assertThat(m.getCuerpo()).contains("\"reservaId\":5").contains(m.getEventId());
    }

    @Test
    void enPreparacion_usaRoutingKeyDeTicket() {
        assertThat(guardado(() -> publisher.publicarEnPreparacion(reserva(EstadoReserva.EN_PREPARACION))).getRoutingKey())
                .isEqualTo("rk.prep");
    }

    @Test
    void enUso_usaRoutingKeyDeVoucher() {
        assertThat(guardado(() -> publisher.publicarEnUso(reserva(EstadoReserva.EN_USO))).getRoutingKey())
                .isEqualTo("rk.voucher");
    }

    private MensajeOutbox guardado(Runnable accion) {
        accion.run();
        ArgumentCaptor<MensajeOutbox> captor = ArgumentCaptor.forClass(MensajeOutbox.class);
        verify(outbox).save(captor.capture());
        return captor.getValue();
    }

    private Reserva reserva(EstadoReserva estado) {
        return Reserva.builder().id(5L).recursoId(10L).usuarioSolicitanteId(20L).estado(estado).build();
    }
}
