package com.campuslab.bookings.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ReservaRequestDTO {

    @NotNull(message = "recursoId es obligatorio")
    private Long recursoId;

    @NotNull(message = "usuarioSolicitanteId es obligatorio")
    private Long usuarioSolicitanteId;

    @NotNull(message = "fechaInicio es obligatoria")
    @Future(message = "fechaInicio debe ser una fecha futura")
    private LocalDateTime fechaInicio;

    @NotNull(message = "fechaFin es obligatoria")
    @Future(message = "fechaFin debe ser una fecha futura")
    private LocalDateTime fechaFin;

    @Size(max = 500, message = "observaciones no puede superar 500 caracteres")
    private String observaciones;
}
