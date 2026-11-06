package com.facturacion.application.port.out;

import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.PersonRole;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Puerto de salida (SPI) hacia el registro de personas. Implementado por el adaptador Cassandra. */
public interface PersonRepositoryPort {

    Person save(Person person);

    Optional<Person> findByDocument(String document);

    List<Person> findAll();

    List<Person> findByRole(PersonRole role);

    /** Consulta asincrona: se ejecuta en el pool "procesosExecutor". */
    CompletableFuture<Optional<Person>> findByDocumentAsync(String document);

    boolean existsByDocument(String document);

    void deleteByDocument(String document);
}
