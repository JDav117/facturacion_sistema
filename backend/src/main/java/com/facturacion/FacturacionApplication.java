package com.facturacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Punto de arranque. @EnableAsync activa el subsistema de hilos de Spring para los metodos @Async.
 */
@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
public class FacturacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FacturacionApplication.class, args);
    }
}
