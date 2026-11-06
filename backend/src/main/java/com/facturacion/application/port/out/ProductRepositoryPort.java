package com.facturacion.application.port.out;

import com.facturacion.domain.model.Product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Puerto de salida (SPI) hacia el catalogo de productos. Implementado por el adaptador MongoDB. */
public interface ProductRepositoryPort {

    Product save(Product product);

    Optional<Product> findById(String id);

    List<Product> findAll();

    /** Productos activos con stock mayor que cero (consulta de disponibilidad). */
    List<Product> findAvailable();

    /** Consulta asincrona: se ejecuta en el pool "procesosExecutor". */
    CompletableFuture<List<Product>> findAllByIdsAsync(Collection<String> ids);

    /** Descuenta stock de forma atomica. Devuelve false si no habia unidades suficientes. */
    boolean decreaseStock(String id, int quantity);

    boolean existsById(String id);

    void deleteById(String id);
}
