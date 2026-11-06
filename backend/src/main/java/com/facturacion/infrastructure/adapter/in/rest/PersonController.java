package com.facturacion.infrastructure.adapter.in.rest;

import com.facturacion.application.port.in.GetPersonsUseCase;
import com.facturacion.application.port.in.RegisterPersonUseCase;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.PersonRole;
import com.facturacion.infrastructure.adapter.in.rest.dto.PersonDtos.PersonRequest;
import com.facturacion.infrastructure.adapter.in.rest.dto.PersonDtos.PersonResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador de entrada REST para personas (Cassandra). */
@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final RegisterPersonUseCase registerPerson;
    private final GetPersonsUseCase getPersons;

    public PersonController(RegisterPersonUseCase registerPerson, GetPersonsUseCase getPersons) {
        this.registerPerson = registerPerson;
        this.getPersons = getPersons;
    }

    @GetMapping
    public List<PersonResponse> list(@RequestParam(required = false) PersonRole role) {
        List<Person> persons = role == null ? getPersons.findAll() : getPersons.findByRole(role);
        return persons.stream().map(PersonResponse::fromDomain).toList();
    }

    @GetMapping("/{document}")
    public PersonResponse get(@PathVariable String document) {
        return PersonResponse.fromDomain(getPersons.findByDocument(document));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse register(@Valid @RequestBody PersonRequest request) {
        return PersonResponse.fromDomain(registerPerson.register(request.toDomain()));
    }

    @PutMapping("/{document}")
    public PersonResponse update(@PathVariable String document, @Valid @RequestBody PersonRequest request) {
        return PersonResponse.fromDomain(registerPerson.update(document, request.toDomain()));
    }

    @DeleteMapping("/{document}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String document) {
        registerPerson.delete(document);
    }
}
