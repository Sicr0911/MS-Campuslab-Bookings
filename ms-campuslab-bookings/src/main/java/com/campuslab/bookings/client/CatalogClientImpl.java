package com.campuslab.bookings.client;

import com.campuslab.bookings.dto.CatalogResourceDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class CatalogClientImpl implements CatalogClient {

    private final RestClient restClient;

    public CatalogClientImpl(@Value("${campuslab.services.catalog-base-url}") String catalogBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(catalogBaseUrl)
                .build();
    }

    @Override
    public CatalogResourceDTO obtenerRecurso(Long recursoId) {
        try {
            return restClient.get()
                    .uri("/api/catalog/resources/{id}", recursoId)
                    .retrieve()
                    .body(CatalogResourceDTO.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CatalogClientException(
                    "El recurso " + recursoId + " no existe en el catalogo", ex);
        } catch (RestClientException ex) {
            throw new CatalogClientException(
                    "No fue posible consultar el catalogo para el recurso " + recursoId, ex);
        }
    }

    @Override
    public void ajustarStock(Long recursoId, int delta) {
        CatalogResourceDTO actual = obtenerRecurso(recursoId);
        int nuevoStock = Math.max(0, actual.getStockCupo() + delta);
        try {
            restClient.put()
                    .uri("/api/catalog/resources/{id}", recursoId)
                    .body(Map.of("stockCupo", nuevoStock))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new CatalogClientException(
                    "No fue posible actualizar el stock del recurso " + recursoId + " en el catalogo", ex);
        }
    }
}
