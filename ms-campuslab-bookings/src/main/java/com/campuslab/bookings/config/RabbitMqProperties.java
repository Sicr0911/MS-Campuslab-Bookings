package com.campuslab.bookings.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Nombres de exchange y routing keys con los que bookings publica comandos
 * a RabbitMQ, centralizados en application.yml (prefijo campuslab.rabbitmq).
 * Deben coincidir con los bindings que declara ms-campuslab-notify
 * (RabbitMqConfig) y con infra/mq/definitions.json.
 */
@Validated
@ConfigurationProperties(prefix = "campuslab.rabbitmq")
public record RabbitMqProperties(
        @NotBlank String exchange,
        @Valid @NotNull RoutingKeys routingKeys
) {

    /**
     * @param email   notificacion email/push al estudiante (q.cmd.email)
     * @param prep    ticket de preparacion para el tecnico (q.cmd.prep)
     * @param voucher generacion del vale/acta en PDF (q.cmd.voucher)
     */
    public record RoutingKeys(
            @NotBlank String email,
            @NotBlank String prep,
            @NotBlank String voucher
    ) {
    }
}
