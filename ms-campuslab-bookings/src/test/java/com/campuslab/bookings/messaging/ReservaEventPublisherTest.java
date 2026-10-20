package com.campuslab.bookings.messaging;

import com.campuslab.bookings.config.RabbitMqProperties;
import com.campuslab.bookings.model.EstadoReserva;
import com.campuslab.bookings.model.Reserva;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReservaEventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private ReservaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        RabbitMqProperties props = new RabbitMqProperties("x.prueba",
                new RabbitMqProperties.RoutingKeys("rk.email", "rk.prep", "rk.voucher"));
        publisher = new ReservaEventPublisher(rabbitTemplate, new ObjectMapper().findAndRegisterModules(), props);
    }

    @Test
    void aprobada_usaExchangeYRoutingKeyDeEmailDesdeLaConfiguracion() {
        publisher.publicarAprobada(reserva(EstadoReserva.APROBADA));

        verify(rabbitTemplate).convertAndSend(eq("x.prueba"), eq("rk.email"), contains("RESERVA_APROBADA"));
    }

    @Test
    void enPreparacion_usaRoutingKeyDeTicket() {
        publisher.publicarEnPreparacion(reserva(EstadoReserva.EN_PREPARACION));

        verify(rabbitTemplate).convertAndSend(eq("x.prueba"), eq("rk.prep"), contains("RESERVA_EN_PREPARACION"));
    }

    @Test
    void enUso_usaRoutingKeyDeVoucher() {
        publisher.publicarEnUso(reserva(EstadoReserva.EN_USO));

        verify(rabbitTemplate).convertAndSend(eq("x.prueba"), eq("rk.voucher"), contains("RESERVA_EN_USO"));
    }

    @Test
    void brokerCaido_noInterrumpeLaTransaccionDeNegocio() {
        doThrow(new AmqpConnectException(new RuntimeException("sin conexion")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        assertThatCode(() -> publisher.publicarCancelada(reserva(EstadoReserva.CANCELADA)))
                .doesNotThrowAnyException();
    }

    private Reserva reserva(EstadoReserva estado) {
        return Reserva.builder().id(5L).recursoId(10L).usuarioSolicitanteId(20L).estado(estado).build();
    }
}
