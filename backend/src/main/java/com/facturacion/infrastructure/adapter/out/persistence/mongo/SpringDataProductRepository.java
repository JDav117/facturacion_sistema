package com.facturacion.infrastructure.adapter.out.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringDataProductRepository extends MongoRepository<ProductDocument, String> {

    List<ProductDocument> findByActiveTrueAndStockGreaterThanOrderByNameAsc(int stock);

    List<ProductDocument> findAllByOrderByNameAsc();
}
