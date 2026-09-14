package com.campuslab.bookings.dto;

import com.campuslab.bookings.model.EstadoReserva;
import com.campuslab.bookings.model.Reserva;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaResponseDTO {

    private Long id;
    private Long recursoId;
    private Long usuarioSolicitanteId;
    private EstadoReserva estado;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String observaciones;
    private Long aprobadoPor;
    private LocalDateTime fechaAprobacion;
    private String motivoCancelacion;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public static ReservaResponseDTO fromEntity(Reserva r) {
        return ReservaResponseDTO.builder()
                .id(r.getId())
                .recursoId(r.getRecursoId())
                .usuarioSolicitanteId(r.getUsuarioSolicitanteId())
                .estado(r.getEstado())
                .fechaInicio(r.getFechaInicio())
                .fechaFin(r.getFechaFin())
                .observaciones(r.getObservaciones())
                .aprobadoPor(r.getAprobadoPor())
                .fechaAprobacion(r.getFechaAprobacion())
                .motivoCancelacion(r.getMotivoCancelacion())
                .fechaCreacion(r.getFechaCreacion())
                .fechaActualizacion(r.getFechaActualizacion())
                .build();
    }
}
