package com.campuslab.bookings.repository;

import com.campuslab.bookings.model.EstadoReserva;
import com.campuslab.bookings.model.Reserva;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * Construye Specifications combinables para el endpoint de listado con filtros
 * (GET /api/bookings?estado=...&recursoId=...&usuarioSolicitanteId=...&desde=...&hasta=...).
 * Cada metodo devuelve null si el filtro no aplica, para que Specification.where(...)
 * los ignore limpiamente al combinarlos con .and(...).
 */
public class ReservaSpecifications {

    private ReservaSpecifications() {
    }

    public static Specification<Reserva> conEstado(EstadoReserva estado) {
        return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
    }

    public static Specification<Reserva> conRecursoId(Long recursoId) {
        return (root, query, cb) -> recursoId == null ? null : cb.equal(root.get("recursoId"), recursoId);
    }

    public static Specification<Reserva> conUsuarioSolicitanteId(Long usuarioId) {
        return (root, query, cb) -> usuarioId == null ? null : cb.equal(root.get("usuarioSolicitanteId"), usuarioId);
    }

    public static Specification<Reserva> conFechaInicioDesde(LocalDateTime desde) {
        return (root, query, cb) -> desde == null ? null : cb.greaterThanOrEqualTo(root.get("fechaInicio"), desde);
    }

    public static Specification<Reserva> conFechaFinHasta(LocalDateTime hasta) {
        return (root, query, cb) -> hasta == null ? null : cb.lessThanOrEqualTo(root.get("fechaFin"), hasta);
    }
}
