package com.facturacion.infrastructure.adapter.out.persistence.cassandra;

import com.facturacion.domain.model.DocumentType;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.PersonRole;
import com.facturacion.domain.model.PersonType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.Indexed;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

/**
 * Fila de la tabla facturacion.personas. Modelo plano (sin JOINs): todo lo que se necesita
 * para mostrar o facturar a una persona se lee con una sola consulta por clave de particion.
 */
@Table("personas")
public class PersonEntity {

    @PrimaryKey("documento")
    private String document;

    @Column("tipo_documento")
    private String documentType;

    @Column("tipo_persona")
    private String personType;

    @Column("nombre")
    private String fullName;

    @Column("correo")
    private String email;

    @Column("telefono")
    private String phone;

    @Column("direccion")
    private String address;

    @Column("ciudad")
    private String city;

    @Indexed("personas_rol_idx")
    @Column("rol")
    private String role;

    @Column("fecha_registro")
    private Instant registeredAt;

    protected PersonEntity() {
    }

    static PersonEntity fromDomain(Person person) {
        PersonEntity entity = new PersonEntity();
        entity.document = person.document();
        entity.documentType = person.documentType().name();
        entity.personType = person.personType().name();
        entity.fullName = person.fullName();
        entity.email = person.email();
        entity.phone = person.phone();
        entity.address = person.address();
        entity.city = person.city();
        entity.role = person.role().name();
        entity.registeredAt = person.registeredAt();
        return entity;
    }

    Person toDomain() {
        return new Person(
                document,
                DocumentType.valueOf(documentType),
                PersonType.valueOf(personType),
                fullName,
                email,
                phone,
                address,
                city,
                PersonRole.valueOf(role),
                registeredAt);
    }
}
