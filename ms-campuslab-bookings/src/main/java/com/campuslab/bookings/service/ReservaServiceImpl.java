package com.campuslab.bookings.service;

import com.campuslab.bookings.client.CatalogClient;
import com.campuslab.bookings.client.CatalogClientException;
import com.campuslab.bookings.dto.CambioEstadoRequestDTO;
import com.campuslab.bookings.dto.CatalogResourceDTO;
import com.campuslab.bookings.dto.ReservaRequestDTO;
import com.campuslab.bookings.dto.ReservaResponseDTO;
import com.campuslab.bookings.exception.ReservaNotFoundException;
import com.campuslab.bookings.exception.TransicionEstadoInvalidaException;
import com.campuslab.bookings.messaging.ReservaEventPublisher;
import com.campuslab.bookings.messaging.ReservaKafkaEventPublisher;
import com.campuslab.bookings.model.EstadoReserva;
import com.campuslab.bookings.model.Reserva;
import com.campuslab.bookings.repository.ReservaRepository;
import com.campuslab.bookings.repository.ReservaSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements ReservaService {

    private static final Set<String> ROLES_QUE_PUEDEN_APROBAR = Set.of("TECNICO", "ADMIN");

    private final ReservaRepository reservaRepository;
    private final CatalogClient catalogClient;
    private final ReservaEventPublisher eventPublisher;
    private final ReservaKafkaEventPublisher kafkaEventPublisher;

    @Override
    @Transactional
    public ReservaResponseDTO crear(ReservaRequestDTO request, Long usuarioSolicitanteId) {
        if (!request.getFechaFin().isAfter(request.getFechaInicio())) {
            throw new IllegalArgumentException("fechaFin debe ser posterior a fechaInicio");
        }

        Reserva reserva = Reserva.builder()
                .recursoId(request.getRecursoId())
                .usuarioSolicitanteId(usuarioSolicitanteId)
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .observaciones(request.getObservaciones())
                .estado(EstadoReserva.SOLICITADA)
                .build();

        Reserva guardada = reservaRepository.save(reserva);
        kafkaEventPublisher.publicar("RESERVA_CREADA", guardada);
        return ReservaResponseDTO.fromEntity(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponseDTO obtenerPorId(Long id) {
        return ReservaResponseDTO.fromEntity(buscarOFallar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservaResponseDTO> listar(EstadoReserva estado,
                                            Long recursoId,
                                            Long usuarioSolicitanteId,
                                            LocalDateTime desde,
                                            LocalDateTime hasta,
                                            Pageable pageable) {
        Specification<Reserva> spec = Specification.where(ReservaSpecifications.conEstado(estado))
                .and(ReservaSpecifications.conRecursoId(recursoId))
                .and(ReservaSpecifications.conUsuarioSolicitanteId(usuarioSolicitanteId))
                .and(ReservaSpecifications.conFechaInicioDesde(desde))
                .and(ReservaSpecifications.conFechaFinHasta(hasta));

        return reservaRepository.findAll(spec, pageable)
                .map(ReservaResponseDTO::fromEntity);
    }

    @Override
    @Transactional
    public ReservaResponseDTO cambiarEstado(Long id,
                                             CambioEstadoRequestDTO request,
                                             Long usuarioQueEjecutaId,
                                             Set<String> rolesUsuario) {
        Reserva reserva = buscarOFallar(id);
        EstadoReserva estadoActual = reserva.getEstado();
        EstadoReserva estadoDestino = request.getNuevoEstado();

        validarTransicionTopologica(estadoActual, estadoDestino);
        validarReglasDeNegocioPorEstadoDestino(reserva, estadoDestino, rolesUsuario, request);

        aplicarEfectosColaterales(reserva, estadoActual, estadoDestino, usuarioQueEjecutaId, request);
        reserva.setEstado(estadoDestino);

        Reserva actualizada = reservaRepository.save(reserva);
        kafkaEventPublisher.publicar("RESERVA_" + estadoDestino.name(), actualizada);
        return ReservaResponseDTO.fromEntity(actualizada);
    }

    // ------------------------------------------------------------------
    // Validaciones de la maquina de estados
    // ------------------------------------------------------------------

    private void validarTransicionTopologica(EstadoReserva actual, EstadoReserva destino) {
        if (actual == destino) {
            throw new TransicionEstadoInvalidaException(
                    "La reserva ya se encuentra en el estado " + destino);
        }
        if (actual.esEstadoTerminal()) {
            throw new TransicionEstadoInvalidaException(
                    "La reserva esta en estado terminal (" + actual + ") y no admite mas cambios de estado");
        }
        if (!actual.puedeTransicionarA(destino)) {
            throw new TransicionEstadoInvalidaException(
                    "Transicion invalida: no se puede pasar de " + actual + " a " + destino);
        }
    }

    /**
     * REGLA DE NEGOCIO CRITICA:
     * No se puede pasar una reserva a EN_USO si no fue APROBADA previamente por
     * un usuario con rol TECNICO o ADMIN. Se valida en dos capas de defensa:
     *   1) rol del usuario que ejecuta la transicion a APROBADA (aqui, por si el
     *      cambio de estado a APROBADA llega hasta el servicio sin pasar el filtro
     *      de seguridad del controlador).
     *   2) que exista un registro formal de aprobacion (aprobadoPor/fechaAprobacion)
     *      antes de permitir el salto a EN_USO.
     */
    private void validarReglasDeNegocioPorEstadoDestino(Reserva reserva,
                                                          EstadoReserva destino,
                                                          Set<String> rolesUsuario,
                                                          CambioEstadoRequestDTO request) {
        switch (destino) {
            case APROBADA -> {
                if (rolesUsuario == null || rolesUsuario.stream().noneMatch(ROLES_QUE_PUEDEN_APROBAR::contains)) {
                    throw new TransicionEstadoInvalidaException(
                            "Solo un usuario con rol TECNICO o ADMIN puede aprobar una reserva");
                }
                validarStockDisponibleEnCatalogo(reserva);
            }
            case EN_USO -> {
                if (!reserva.tieneAprobacionRegistrada()) {
                    throw new TransicionEstadoInvalidaException(
                            "No se puede pasar la reserva a EN_USO: no ha sido APROBADA previamente "
                                    + "por un tecnico o admin");
                }
            }
            case CANCELADA -> {
                if (request.getMotivoCancelacion() == null || request.getMotivoCancelacion().isBlank()) {
                    throw new IllegalArgumentException("motivoCancelacion es obligatorio para cancelar una reserva");
                }
            }
            default -> {
                // EN_PREPARACION y DEVUELTA no requieren validaciones adicionales de rol/estado.
            }
        }
    }

    /**
     * Consulta a ms-campuslab-catalog el recurso reservado y bloquea la
     * aprobacion si no queda stock/cupo disponible. Si el catalogo no
     * responde (caido, timeout, recurso inexistente) la aprobacion tambien
     * se bloquea: no se puede aprobar sin poder verificar la disponibilidad real.
     */
    private void validarStockDisponibleEnCatalogo(Reserva reserva) {
        CatalogResourceDTO recurso;
        try {
            recurso = catalogClient.obtenerRecurso(reserva.getRecursoId());
        } catch (CatalogClientException ex) {
            throw new TransicionEstadoInvalidaException(
                    "No se pudo verificar la disponibilidad del recurso " + reserva.getRecursoId()
                            + " en el catalogo: " + ex.getMessage());
        }

        if (recurso.getStockCupo() == null || recurso.getStockCupo() <= 0) {
            throw new TransicionEstadoInvalidaException(
                    "No es posible aprobar la reserva: el recurso " + reserva.getRecursoId()
                            + " no tiene stock/cupo disponible en el catalogo");
        }
    }

    /**
     * Efectos colaterales de una transicion de estado: registro de aprobacion,
     * ajuste de stock/cupo en catalog y publicacion del evento correspondiente
     * a RabbitMQ para que notify avise al estudiante/tecnico. El stock se
     * descuenta al aprobar (se reserva el cupo) y se devuelve si la reserva
     * se cancela despues de haber sido aprobada, o cuando el recurso se
     * marca DEVUELTA (vuelve a estar disponible).
     */
    private void aplicarEfectosColaterales(Reserva reserva,
                                            EstadoReserva actual,
                                            EstadoReserva destino,
                                            Long usuarioQueEjecutaId,
                                            CambioEstadoRequestDTO request) {
        switch (destino) {
            case APROBADA -> {
                reserva.setAprobadoPor(usuarioQueEjecutaId);
                reserva.setFechaAprobacion(LocalDateTime.now());
                catalogClient.ajustarStock(reserva.getRecursoId(), -1);
                eventPublisher.publicarAprobada(reserva);
            }
            case EN_PREPARACION -> eventPublisher.publicarEnPreparacion(reserva);
            case EN_USO -> eventPublisher.publicarEnUso(reserva);
            case CANCELADA -> {
                reserva.setMotivoCancelacion(request.getMotivoCancelacion());
                if (actual != EstadoReserva.SOLICITADA) {
                    // Ya se habia descontado el stock al aprobar; se devuelve.
                    catalogClient.ajustarStock(reserva.getRecursoId(), 1);
                }
                eventPublisher.publicarCancelada(reserva);
            }
            case DEVUELTA -> {
                catalogClient.ajustarStock(reserva.getRecursoId(), 1);
                eventPublisher.publicarDevuelta(reserva);
            }
            default -> {
                // SOLICITADA no es un destino valido de transicion (topologia lo impide).
            }
        }
    }

    private Reserva buscarOFallar(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new ReservaNotFoundException(id));
    }
}
