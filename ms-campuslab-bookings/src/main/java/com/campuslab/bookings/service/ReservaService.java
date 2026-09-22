package com.campuslab.bookings.service;

import com.campuslab.bookings.dto.CambioEstadoRequestDTO;
import com.campuslab.bookings.dto.ReservaRequestDTO;
import com.campuslab.bookings.dto.ReservaResponseDTO;
import com.campuslab.bookings.model.EstadoReserva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface ReservaService {

    /**
     * @param usuarioSolicitanteId id del usuario autenticado que solicita la reserva,
     *                             derivado del JWT (nunca autoreportado por el cliente:
     *                             evita que alguien cree una reserva "a nombre de" otro usuario)
     */
    ReservaResponseDTO crear(ReservaRequestDTO request, Long usuarioSolicitanteId);

    ReservaResponseDTO obtenerPorId(Long id);

    Page<ReservaResponseDTO> listar(EstadoReserva estado,
                                     Long recursoId,
                                     Long usuarioSolicitanteId,
                                     LocalDateTime desde,
                                     LocalDateTime hasta,
                                     Pageable pageable);

    /**
     * Cambia el estado de una reserva aplicando la maquina de estados y las reglas
     * de negocio asociadas (incluida la validacion critica de aprobacion previa
     * para llegar a EN_USO).
     *
     * @param id                 id de la reserva
     * @param request            nuevo estado solicitado (+ motivo si aplica)
     * @param usuarioQueEjecutaId id del usuario autenticado que ejecuta la accion
     * @param rolesUsuario       roles del usuario autenticado (ej. "TECNICO", "ADMIN", "ESTUDIANTE")
     */
    ReservaResponseDTO cambiarEstado(Long id,
                                      CambioEstadoRequestDTO request,
                                      Long usuarioQueEjecutaId,
                                      java.util.Set<String> rolesUsuario);
}
