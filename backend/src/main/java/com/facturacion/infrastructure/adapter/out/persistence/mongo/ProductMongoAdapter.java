package com.facturacion.infrastructure.adapter.out.persistence.mongo;

import com.facturacion.application.port.out.ProductRepositoryPort;
import com.facturacion.domain.model.Product;
import com.facturacion.infrastructure.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Adaptador de salida: implementa ProductRepositoryPort sobre MongoDB. */
@Component
public class ProductMongoAdapter implements ProductRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(ProductMongoAdapter.class);

    private final SpringDataProductRepository repository;
    private final MongoTemplate mongoTemplate;

    public ProductMongoAdapter(SpringDataProductRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Product save(Product product) {
        return repository.save(ProductDocument.fromDomain(product)).toDomain();
    }

    @Override
    public Optional<Product> findById(String id) {
        return repository.findById(id).map(ProductDocument::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(ProductDocument::toDomain).toList();
    }

    @Override
    public List<Product> findAvailable() {
        return repository.findByActiveTrueAndStockGreaterThanOrderByNameAsc(0).stream()
                .map(ProductDocument::toDomain).toList();
    }

    @Async(AsyncConfig.EXECUTOR)
    @Override
    public CompletableFuture<List<Product>> findAllByIdsAsync(Collection<String> ids) {
        log.info("Consultando {} producto(s) en MongoDB", ids.size());
        List<Product> products = repository.findAllById(ids).stream().map(ProductDocument::toDomain).toList();
        return CompletableFuture.completedFuture(products);
    }

    /** Descuento atomico: solo actualiza si stock >= cantidad (evita stock negativo con ventas concurrentes). */
    @Override
    public boolean decreaseStock(String id, int quantity) {
        Query query = new Query(Criteria.where("id").is(id).and("stock").gte(quantity));
        Update update = new Update().inc("stock", -quantity).currentDate("fechaActualizacion");
        return mongoTemplate.updateFirst(query, update, ProductDocument.class).getModifiedCount() == 1;
    }

    @Override
    public boolean existsById(String id) {
        return repository.existsById(id);
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }
}
