package com.facturacion.domain.model;

import com.facturacion.domain.exception.DomainException;

import java.time.Instant;

/**
 * Persona del sistema (cliente, empleado o proveedor; natural o juridica).
 * El documento de identificacion es la clave en Cassandra.
 */
public record Person(
        String document,
        DocumentType documentType,
        PersonType personType,
        String fullName,
        String email,
        String phone,
        String address,
        String city,
        PersonRole role,
        Instant registeredAt) {

    public Person {
        if (document == null || document.isBlank()) {
            throw new DomainException("El documento de identificacion es obligatorio");
        }
        if (documentType == null) {
            throw new DomainException("El tipo de documento es obligatorio");
        }
        if (personType == null) {
            throw new DomainException("Debe indicar si es persona natural o juridica");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new DomainException("El nombre o razon social es obligatorio");
        }
        if (role == null) {
            throw new DomainException("El rol de la persona es obligatorio");
        }
        if (email != null && !email.isBlank() && !email.contains("@")) {
            throw new DomainException("El correo electronico no es valido");
        }
        if (personType == PersonType.JURIDICA && documentType != DocumentType.NIT) {
            throw new DomainException("Una persona juridica debe identificarse con NIT");
        }
        document = document.trim();
    }

    public boolean isClient() {
        return role == PersonRole.CLIENTE;
    }

    public Person withDocument(String newDocument) {
        return new Person(newDocument, documentType, personType, fullName, email, phone, address, city, role, registeredAt);
    }

    public Person withRegisteredAt(Instant instant) {
        return new Person(document, documentType, personType, fullName, email, phone, address, city, role, instant);
    }
}
