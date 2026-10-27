package com.campuslab.bookings.messaging;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un comando para RabbitMQ guardado en la misma transaccion que el cambio
 * de estado de la reserva (patron outbox). OutboxRelay lo envia despues.
 */
@Entity
@Table(name = "OUTBOX_RABBIT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MensajeOutbox {

    public enum Estado { PENDIENTE, ENVIADO, FALLIDO }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_rabbit_seq_gen")
    @SequenceGenerator(name = "outbox_rabbit_seq_gen", sequenceName = "OUTBOX_RABBIT_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "EVENT_ID", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "TIPO", nullable = false, length = 40)
    private String tipo;

    @Column(name = "EXCHANGE_NAME", nullable = false, length = 120)
    private String exchange;

    @Column(name = "ROUTING_KEY", nullable = false, length = 120)
    private String routingKey;

    @Lob
    @Column(name = "CUERPO", nullable = false)
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private Estado estado;

    @Column(name = "INTENTOS", nullable = false)
    private int intentos;

    @Column(name = "ULTIMO_ERROR", length = 500)
    private String ultimoError;

    @Column(name = "CREADO_EN", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "ENVIADO_EN")
    private LocalDateTime enviadoEn;

    @PrePersist
    protected void alCrear() {
        this.creadoEn = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = Estado.PENDIENTE;
        }
    }

    public void registrarFallo(String motivo) {
        this.intentos++;
        this.ultimoError = motivo == null ? null : motivo.substring(0, Math.min(motivo.length(), 500));
    }
}
