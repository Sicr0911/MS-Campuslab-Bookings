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
}
