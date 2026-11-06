package com.facturacion.infrastructure.concurrency;

import com.facturacion.application.port.in.GetPersonsUseCase;
import com.facturacion.domain.model.PersonRole;
import com.facturacion.infrastructure.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/** Proceso concurrente 3: prepara notificaciones para los clientes registrados (Cassandra). */
@Component
public class ProcesoNotificaciones {

    private static final Logger log = LoggerFactory.getLogger(ProcesoNotificaciones.class);

    private final GetPersonsUseCase getPersons;

    public ProcesoNotificaciones(GetPersonsUseCase getPersons) {
        this.getPersons = getPersons;
    }

    @Async(AsyncConfig.EXECUTOR)
    public CompletableFuture<String> ejecutar(int iteraciones) {
        String hilo = Thread.currentThread().getName();
        for (int i = 1; i <= iteraciones; i++) {
            log.info("[{}] NOTIFICACIONES  -> paso {}/{}", hilo, i, iteraciones);
            Pausa.aleatoria();
        }
        try {
            int clientes = getPersons.findByRole(PersonRole.CLIENTE).size();
            log.info("[{}] NOTIFICACIONES  -> terminado. {} cliente(s) listos para notificar", hilo, clientes);
        } catch (RuntimeException e) {
            log.warn("[{}] NOTIFICACIONES  -> Cassandra no disponible: {}", hilo, e.getMessage());
        }
        return CompletableFuture.completedFuture("Notificaciones");
    }
}
