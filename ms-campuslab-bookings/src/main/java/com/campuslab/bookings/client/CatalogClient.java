package com.campuslab.bookings.client;

import com.campuslab.bookings.dto.CatalogResourceDTO;

/**
 * Cliente HTTP hacia ms-campuslab-catalog. Se usa para consultar el
 * stock/cupo real de un recurso antes de aprobar una reserva.
 */
public interface CatalogClient {

    /**
     * Obtiene el recurso del catalogo por id.
     *
     * @throws CatalogClientException si el recurso no existe en el catalogo
     *                                 o si el catalogo no responde correctamente.
     */
    CatalogResourceDTO obtenerRecurso(Long recursoId);

    /**
     * Ajusta (suma/resta) el stock/cupo de un recurso en el catalogo.
     * Usado para descontar stock al aprobar una reserva y devolverlo al
     * cancelarla (si ya se habia descontado) o al marcarla DEVUELTA.
     *
     * @param delta positivo para incrementar, negativo para decrementar.
     * @throws CatalogClientException si el recurso no existe o el catalogo no responde.
     */
    void ajustarStock(Long recursoId, int delta);
}
