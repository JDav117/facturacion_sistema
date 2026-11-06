package com.facturacion.infrastructure.concurrency;

import com.facturacion.application.port.in.GetProductsUseCase;
import com.facturacion.domain.model.Product;
import com.facturacion.infrastructure.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Proceso concurrente 1: revisa el inventario (MongoDB) y alerta productos con poco stock. */
@Component
public class ProcesoInventario {

    private static final Logger log = LoggerFactory.getLogger(ProcesoInventario.class);
    private static final int STOCK_MINIMO = 5;

    private final GetProductsUseCase getProducts;

    public ProcesoInventario(GetProductsUseCase getProducts) {
        this.getProducts = getProducts;
    }

    @Async(AsyncConfig.EXECUTOR)
    public CompletableFuture<String> ejecutar(int iteraciones) {
        String hilo = Thread.currentThread().getName();
        for (int i = 1; i <= iteraciones; i++) {
            log.info("[{}] INVENTARIO      -> paso {}/{}", hilo, i, iteraciones);
            Pausa.aleatoria();
        }
        try {
            List<String> bajos = getProducts.getCatalog().stream()
                    .filter(p -> p.stock() < STOCK_MINIMO)
                    .map(Product::name)
                    .toList();
            log.info("[{}] INVENTARIO      -> terminado. Productos con stock < {}: {}", hilo, STOCK_MINIMO, bajos);
        } catch (RuntimeException e) {
            log.warn("[{}] INVENTARIO      -> MongoDB no disponible: {}", hilo, e.getMessage());
        }
        return CompletableFuture.completedFuture("Inventario");
    }
}
