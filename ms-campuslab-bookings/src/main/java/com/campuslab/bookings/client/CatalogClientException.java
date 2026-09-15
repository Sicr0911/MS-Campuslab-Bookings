package com.campuslab.bookings.client;

/**
 * Se lanza cuando ms-campuslab-catalog no puede resolver un recurso
 * (no existe) o cuando la llamada HTTP falla (timeout, 5xx, conexion rechazada).
 */
public class CatalogClientException extends RuntimeException {

    public CatalogClientException(String message) {
        super(message);
    }

    public CatalogClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
