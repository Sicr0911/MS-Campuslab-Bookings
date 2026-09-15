package com.campuslab.bookings.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representa la parte del recurso de ms-campuslab-catalog que necesita
 * ms-campuslab-bookings para validar la aprobacion de una reserva.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogResourceDTO {

    private Long id;
    private String name;
    private String resourceType;
    private Integer stockCupo;
}
