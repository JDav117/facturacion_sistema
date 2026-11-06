package com.facturacion.application.port.in;

import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.PersonRole;

import java.util.List;

/** Puerto de entrada: consultar personas. */
public interface GetPersonsUseCase {

    List<Person> findAll();

    List<Person> findByRole(PersonRole role);

    Person findByDocument(String document);
}
