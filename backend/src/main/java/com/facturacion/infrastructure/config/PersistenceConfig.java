package com.facturacion.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Cada modulo de Spring Data escanea solo el paquete de su adaptador, para que
 * los tres motores convivan sin ambiguedad.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.facturacion.infrastructure.adapter.out.persistence.mysql")
@EnableMongoRepositories(basePackages = "com.facturacion.infrastructure.adapter.out.persistence.mongo")
@EnableCassandraRepositories(basePackages = "com.facturacion.infrastructure.adapter.out.persistence.cassandra")
public class PersistenceConfig {
}
