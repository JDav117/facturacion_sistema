package com.facturacion.application.service;

import com.facturacion.application.port.in.GetPersonsUseCase;
import com.facturacion.application.port.in.RegisterPersonUseCase;
import com.facturacion.application.port.out.PersonRepositoryPort;
import com.facturacion.domain.exception.DomainException;
import com.facturacion.domain.exception.ResourceNotFoundException;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.PersonRole;

import java.time.Clock;
import java.util.List;

public class PersonService implements RegisterPersonUseCase, GetPersonsUseCase {

    private final PersonRepositoryPort personRepository;
    private final Clock clock;

    public PersonService(PersonRepositoryPort personRepository, Clock clock) {
        this.personRepository = personRepository;
        this.clock = clock;
    }

    @Override
    public Person register(Person person) {
        // Cassandra hace "upsert": sin esta validacion un registro duplicado sobrescribiria al existente.
        if (personRepository.existsByDocument(person.document())) {
            throw new DomainException("Ya existe una persona con documento " + person.document());
        }
        return personRepository.save(person.withRegisteredAt(clock.instant()));
    }

    @Override
    public Person update(String document, Person person) {
        Person current = findByDocument(document);
        return personRepository.save(person.withDocument(document).withRegisteredAt(current.registeredAt()));
    }

    @Override
    public void delete(String document) {
        findByDocument(document);
        personRepository.deleteByDocument(document);
    }

    @Override
    public List<Person> findAll() {
        return personRepository.findAll();
    }

    @Override
    public List<Person> findByRole(PersonRole role) {
        return personRepository.findByRole(role);
    }

    @Override
    public Person findByDocument(String document) {
        return personRepository.findByDocument(document)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la persona con documento " + document));
    }
}
