package com.facturacion.infrastructure.adapter.in.rest.dto;

import com.facturacion.domain.model.DocumentType;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.PersonRole;
import com.facturacion.domain.model.PersonType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class PersonDtos {

    private PersonDtos() {
    }

    public record PersonRequest(
            @NotBlank String document,
            @NotNull DocumentType documentType,
            @NotNull PersonType personType,
            @NotBlank String fullName,
            @Email String email,
            String phone,
            String address,
            String city,
            @NotNull PersonRole role) {

        public Person toDomain() {
            return new Person(document, documentType, personType, fullName.trim(), email, phone, address, city, role, null);
        }
    }

    public record PersonResponse(
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

        public static PersonResponse fromDomain(Person p) {
            return new PersonResponse(p.document(), p.documentType(), p.personType(), p.fullName(), p.email(),
                    p.phone(), p.address(), p.city(), p.role(), p.registeredAt());
        }
    }
}
