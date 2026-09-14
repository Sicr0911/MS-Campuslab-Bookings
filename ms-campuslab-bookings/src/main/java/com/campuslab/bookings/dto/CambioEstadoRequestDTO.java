package com.campuslab.bookings.dto;

import com.campuslab.bookings.model.EstadoReserva;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CambioEstadoRequestDTO {

    @NotNull(message = "nuevoEstado es obligatorio")
    private EstadoReserva nuevoEstado;

    /** Obligatorio solo cuando nuevoEstado = CANCELADA (validado en el servicio). */
    @Size(max = 500)
    private String motivoCancelacion;
}
