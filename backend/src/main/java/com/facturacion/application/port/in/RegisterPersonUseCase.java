package com.facturacion.application.port.in;

import com.facturacion.domain.model.Person;

/** Puerto de entrada: registrar, actualizar y eliminar personas. */
public interface RegisterPersonUseCase {

    Person register(Person person);

    Person update(String document, Person person);

    void delete(String document);
}
