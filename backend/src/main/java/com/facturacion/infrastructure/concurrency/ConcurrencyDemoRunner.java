package com.facturacion.infrastructure.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Al arrancar la aplicacion lanza los tres procesos a la vez. El hilo "main" no los espera:
 * en consola se ve como los pasos de cada proceso se intercalan de forma no determinista.
 */
@Component
@ConditionalOnProperty(name = "facturacion.demo-concurrencia.enabled", havingValue = "true", matchIfMissing = true)
public class ConcurrencyDemoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyDemoRunner.class);

    private final ProcesoInventario inventario;
    private final ProcesoReportes reportes;
    private final ProcesoNotificaciones notificaciones;
    private final int iteraciones;

    public ConcurrencyDemoRunner(ProcesoInventario inventario,
                                 ProcesoReportes reportes,
                                 ProcesoNotificaciones notificaciones,
                                 @Value("${facturacion.demo-concurrencia.iteraciones:5}") int iteraciones) {
        this.inventario = inventario;
        this.reportes = reportes;
        this.notificaciones = notificaciones;
        this.iteraciones = iteraciones;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("==== Lanzando 3 procesos concurrentes en el pool 'procesosExecutor' ====");
        CompletableFuture<String> p1 = inventario.ejecutar(iteraciones);
        CompletableFuture<String> p2 = reportes.ejecutar(iteraciones);
        CompletableFuture<String> p3 = notificaciones.ejecutar(iteraciones);
        log.info("==== Hilo [{}] libre: no espera a los procesos ====", Thread.currentThread().getName());

        CompletableFuture.allOf(p1, p2, p3).thenRun(() ->
                log.info("==== Procesos finalizados: {}, {}, {} ====", p1.join(), p2.join(), p3.join()));
    }
}
