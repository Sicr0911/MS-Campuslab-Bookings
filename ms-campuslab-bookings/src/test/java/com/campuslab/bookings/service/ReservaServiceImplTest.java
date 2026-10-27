package com.campuslab.bookings.service;

import com.campuslab.bookings.client.CatalogClient;
import com.campuslab.bookings.client.CatalogClientException;
import com.campuslab.bookings.dto.CambioEstadoRequestDTO;
import com.campuslab.bookings.dto.CatalogResourceDTO;
import com.campuslab.bookings.exception.TransicionEstadoInvalidaException;
import com.campuslab.bookings.messaging.ReservaEventPublisher;
import com.campuslab.bookings.messaging.ReservaKafkaEventPublisher;
import com.campuslab.bookings.model.EstadoReserva;
import com.campuslab.bookings.model.Reserva;
import com.campuslab.bookings.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ReservaServiceImplTest {

    private ReservaRepository reservaRepository;
    private CatalogClient catalogClient;
    private ReservaEventPublisher eventPublisher;
    private ReservaKafkaEventPublisher kafkaEventPublisher;
    private ReservaServiceImpl service;

    @BeforeEach
    void setUp() {
        reservaRepository = Mockito.mock(ReservaRepository.class);
        catalogClient = Mockito.mock(CatalogClient.class);
        eventPublisher = Mockito.mock(ReservaEventPublisher.class);
        kafkaEventPublisher = Mockito.mock(ReservaKafkaEventPublisher.class);
        service = new ReservaServiceImpl(reservaRepository, catalogClient, eventPublisher, kafkaEventPublisher);
    }

    @Test
    void noDebePermitirEnUsoSiNuncaFueAprobada() {
        Reserva reserva = reservaEnEstado(EstadoReserva.EN_PREPARACION);
        // aprobadoPor / fechaAprobacion quedan null: nunca se aprobo formalmente
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));

        CambioEstadoRequestDTO request = new CambioEstadoRequestDTO();
        request.setNuevoEstado(EstadoReserva.EN_USO);

        assertThatThrownBy(() ->
                service.cambiarEstado(1L, request, 99L, Set.of("TECNICO")))
                .isInstanceOf(TransicionEstadoInvalidaException.class)
                .hasMessageContaining("APROBADA");
    }

    @Test
    void debePermitirEnUsoSiFueAprobadaPreviamente() {
        Reserva reserva = reservaEnEstado(EstadoReserva.EN_PREPARACION);
        reserva.setAprobadoPor(50L);
        reserva.setFechaAprobacion(LocalDateTime.now().minusHours(1));
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        CambioEstadoRequestDTO request = new CambioEstadoRequestDTO();
        request.setNuevoEstado(EstadoReserva.EN_USO);

        var resultado = service.cambiarEstado(1L, request, 99L, Set.of("TECNICO"));

        assertThat(resultado.getEstado()).isEqualTo(EstadoReserva.EN_USO);
    }

    @Test
    void noDebePermitirAprobarSinRolTecnicoOAdmin() {
        Reserva reserva = reservaEnEstado(EstadoReserva.SOLICITADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));

        CambioEstadoRequestDTO request = new CambioEstadoRequestDTO();
        request.setNuevoEstado(EstadoReserva.APROBADA);

        assertThatThrownBy(() ->
                service.cambiarEstado(1L, request, 20L, Set.of("ESTUDIANTE"))) // su propia reserva
                .isInstanceOf(TransicionEstadoInvalidaException.class)
                .hasMessageContaining("tecnico o admin");
    }

    @Test
    void noDebePermitirAprobarSiElCatalogoNoTieneStock() {
        Reserva reserva = reservaEnEstado(EstadoReserva.SOLICITADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(catalogClient.obtenerRecurso(10L))
                .thenReturn(new CatalogResourceDTO(10L, "Microscopio", "EQUIPO", 0));

        CambioEstadoRequestDTO request = new CambioEstadoRequestDTO();
        request.setNuevoEstado(EstadoReserva.APROBADA);

        assertThatThrownBy(() ->
                service.cambiarEstado(1L, request, 99L, Set.of("TECNICO")))
                .isInstanceOf(TransicionEstadoInvalidaException.class)
                .hasMessageContaining("stock/cupo disponible");
    }

    @Test
    void noDebePermitirAprobarSiElCatalogoNoResponde() {
        Reserva reserva = reservaEnEstado(EstadoReserva.SOLICITADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(catalogClient.obtenerRecurso(10L))
                .thenThrow(new CatalogClientException("timeout"));

        CambioEstadoRequestDTO request = new CambioEstadoRequestDTO();
        request.setNuevoEstado(EstadoReserva.APROBADA);

        assertThatThrownBy(() ->
                service.cambiarEstado(1L, request, 99L, Set.of("TECNICO")))
                .isInstanceOf(TransicionEstadoInvalidaException.class)
                .hasMessageContaining("catalogo");
    }

    @Test
    void debePermitirAprobarSiElCatalogoTieneStock() {
        Reserva reserva = reservaEnEstado(EstadoReserva.SOLICITADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        when(catalogClient.obtenerRecurso(10L))
                .thenReturn(new CatalogResourceDTO(10L, "Microscopio", "EQUIPO", 5));

        CambioEstadoRequestDTO request = new CambioEstadoRequestDTO();
        request.setNuevoEstado(EstadoReserva.APROBADA);

        var resultado = service.cambiarEstado(1L, request, 99L, Set.of("TECNICO"));

        assertThat(resultado.getEstado()).isEqualTo(EstadoReserva.APROBADA);
    }

    @Test
    void noDebePermitirTransicionFueraDeTopologia() {
        Reserva reserva = reservaEnEstado(EstadoReserva.SOLICITADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));

        CambioEstadoRequestDTO request = new CambioEstadoRequestDTO();
        request.setNuevoEstado(EstadoReserva.EN_USO); // salto invalido directo

        assertThatThrownBy(() ->
                service.cambiarEstado(1L, request, 99L, Set.of("ADMIN")))
                .isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    @Test
    void estudiantePuedeCancelarSuReservaConJustificacion() {
        Reserva reserva = reservaEnEstado(EstadoReserva.SOLICITADA); // usuarioSolicitanteId = 20
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.cambiarEstado(1L,
                new CambioEstadoRequestDTO(EstadoReserva.CANCELADA, "  Me cambiaron el horario de clases  "),
                20L, Set.of("ESTUDIANTE"));

        assertThat(resultado.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(resultado.getMotivoCancelacion()).isEqualTo("Me cambiaron el horario de clases");
    }

    @Test
    void cancelarSinJustificacionSuficienteSeRechaza() {
        Reserva reserva = reservaEnEstado(EstadoReserva.SOLICITADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> service.cambiarEstado(1L,
                new CambioEstadoRequestDTO(EstadoReserva.CANCELADA, "no voy"), 20L, Set.of("ESTUDIANTE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificar");
    }

    @Test
    void estudianteNoPuedeCancelarReservaAjena() {
        Reserva reserva = reservaEnEstado(EstadoReserva.SOLICITADA); // de usuario 20
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> service.cambiarEstado(1L,
                new CambioEstadoRequestDTO(EstadoReserva.CANCELADA, "Motivo suficientemente largo"),
                77L, Set.of("ESTUDIANTE")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void estudianteNoPuedeHacerOtraTransicionQueCancelar() {
        Reserva reserva = reservaEnEstado(EstadoReserva.APROBADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> service.cambiarEstado(1L,
                new CambioEstadoRequestDTO(EstadoReserva.EN_PREPARACION, null), 20L, Set.of("ESTUDIANTE")))
                .isInstanceOf(TransicionEstadoInvalidaException.class)
                .hasMessageContaining("solo puede cancelar");
    }

    private Reserva reservaEnEstado(EstadoReserva estado) {
        return Reserva.builder()
                .id(1L)
                .recursoId(10L)
                .usuarioSolicitanteId(20L)
                .estado(estado)
                .fechaInicio(LocalDateTime.now().plusHours(1))
                .fechaFin(LocalDateTime.now().plusHours(2))
                .build();
    }
}
