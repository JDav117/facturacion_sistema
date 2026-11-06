package com.facturacion.infrastructure.adapter.out.persistence.cassandra;

import com.facturacion.application.port.out.PersonRepositoryPort;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.PersonRole;
import com.facturacion.infrastructure.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Adaptador de salida: implementa PersonRepositoryPort sobre Apache Cassandra. */
@Component
public class PersonCassandraAdapter implements PersonRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(PersonCassandraAdapter.class);
    private static final Comparator<Person> BY_NAME = Comparator.comparing(Person::fullName, String.CASE_INSENSITIVE_ORDER);

    private final SpringDataPersonRepository repository;

    public PersonCassandraAdapter(SpringDataPersonRepository repository) {
        this.repository = repository;
    }

    @Override
    public Person save(Person person) {
        return repository.save(PersonEntity.fromDomain(person)).toDomain();
    }

    @Override
    public Optional<Person> findByDocument(String document) {
        return repository.findById(document).map(PersonEntity::toDomain);
    }

    @Override
    public List<Person> findAll() {
        return repository.findAll().stream().map(PersonEntity::toDomain).sorted(BY_NAME).toList();
    }

    @Override
    public List<Person> findByRole(PersonRole role) {
        return repository.findByRole(role.name()).stream().map(PersonEntity::toDomain).sorted(BY_NAME).toList();
    }

    @Async(AsyncConfig.EXECUTOR)
    @Override
    public CompletableFuture<Optional<Person>> findByDocumentAsync(String document) {
        log.info("Consultando cliente {} en Cassandra", document);
        return CompletableFuture.completedFuture(findByDocument(document));
    }

    @Override
    public boolean existsByDocument(String document) {
        return repository.existsById(document);
    }

    @Override
    public void deleteByDocument(String document) {
        repository.deleteById(document);
    }
}
