package com.campuslab.bookings.exception;

public class ReservaNotFoundException extends RuntimeException {

    public ReservaNotFoundException(Long id) {
        super("No se encontro la reserva con id=" + id);
    }
}
