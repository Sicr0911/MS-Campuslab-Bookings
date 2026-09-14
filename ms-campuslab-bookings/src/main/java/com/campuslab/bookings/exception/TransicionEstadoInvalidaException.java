package com.campuslab.bookings.exception;

/**
 * Se lanza cuando se intenta una transicion de estado que viola la topologia
 * de la maquina de estados, o una regla de negocio asociada a una transicion
 * (por ejemplo: intentar EN_USO sin aprobacion previa registrada).
 */
public class TransicionEstadoInvalidaException extends RuntimeException {

    public TransicionEstadoInvalidaException(String message) {
        super(message);
    }
}
