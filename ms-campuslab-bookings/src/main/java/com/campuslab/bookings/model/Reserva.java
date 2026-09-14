package com.campuslab.bookings.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "RESERVAS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reservas_seq_gen")
    @SequenceGenerator(name = "reservas_seq_gen", sequenceName = "RESERVAS_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    /**
     * ID del recurso reservado (laboratorio o equipo). Sin FK formal:
     * el recurso puede vivir en este mismo dominio o ser gestionado por otro
     * microservicio; aqui solo se guarda la referencia.
     */
    @Column(name = "RECURSO_ID", nullable = false)
    private Long recursoId;

    @Column(name = "USUARIO_SOLICITANTE_ID", nullable = false)
    private Long usuarioSolicitanteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoReserva estado;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "FECHA_FIN", nullable = false)
    private LocalDateTime fechaFin;

    @Column(name = "OBSERVACIONES", length = 500)
    private String observaciones;

    /** Usuario (id) que aprobo la reserva. Null hasta que exista una aprobacion. */
    @Column(name = "APROBADO_POR")
    private Long aprobadoPor;

    /** Momento en que se registro la aprobacion. Null hasta que exista una aprobacion. */
    @Column(name = "FECHA_APROBACION")
    private LocalDateTime fechaAprobacion;

    @Column(name = "MOTIVO_CANCELACION", length = 500)
    private String motivoCancelacion;

    @Column(name = "FECHA_CREACION", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "FECHA_ACTUALIZACION")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void alCrear() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = EstadoReserva.SOLICITADA;
        }
    }

    @PreUpdate
    protected void alActualizar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    /**
     * True solo si la reserva fue formalmente aprobada (queda registro de quien y cuando).
     * Es la base de la regla critica: no se puede pasar a EN_USO sin esto.
     */
    public boolean tieneAprobacionRegistrada() {
        return this.aprobadoPor != null && this.fechaAprobacion != null;
    }
}
