package com.campuslab.bookings.messaging;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface MensajeOutboxRepository extends JpaRepository<MensajeOutbox, Long> {

    /** Pendientes en orden de llegada: se respeta el orden de los eventos de una reserva. */
    List<MensajeOutbox> findByEstadoOrderByIdAsc(MensajeOutbox.Estado estado, Pageable pageable);

    @Transactional
    long deleteByEstadoAndEnviadoEnBefore(MensajeOutbox.Estado estado, LocalDateTime limite);
}
