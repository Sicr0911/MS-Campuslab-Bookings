package com.campuslab.bookings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // OutboxRelay: envio periodico de los comandos pendientes a RabbitMQ
public class MsCampuslabBookingsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsCampuslabBookingsApplication.class, args);
    }
}
