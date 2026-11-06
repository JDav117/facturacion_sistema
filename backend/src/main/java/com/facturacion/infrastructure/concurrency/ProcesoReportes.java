package com.facturacion.infrastructure.concurrency;

import com.facturacion.application.port.in.GetInvoicesUseCase;
import com.facturacion.domain.model.Invoice;
import com.facturacion.infrastructure.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Proceso concurrente 2: genera un reporte de ventas acumuladas (MySQL). */
@Component
public class ProcesoReportes {

    private static final Logger log = LoggerFactory.getLogger(ProcesoReportes.class);

    private final GetInvoicesUseCase getInvoices;

    public ProcesoReportes(GetInvoicesUseCase getInvoices) {
        this.getInvoices = getInvoices;
    }

    @Async(AsyncConfig.EXECUTOR)
    public CompletableFuture<String> ejecutar(int iteraciones) {
        String hilo = Thread.currentThread().getName();
        for (int i = 1; i <= iteraciones; i++) {
            log.info("[{}] REPORTES        -> paso {}/{}", hilo, i, iteraciones);
            Pausa.aleatoria();
        }
        try {
            List<Invoice> facturas = getInvoices.findAll();
            BigDecimal total = facturas.stream().map(Invoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            log.info("[{}] REPORTES        -> terminado. {} factura(s), ventas acumuladas: {}", hilo, facturas.size(), total);
        } catch (RuntimeException e) {
            log.warn("[{}] REPORTES        -> MySQL no disponible: {}", hilo, e.getMessage());
        }
        return CompletableFuture.completedFuture("Reportes");
    }
}
