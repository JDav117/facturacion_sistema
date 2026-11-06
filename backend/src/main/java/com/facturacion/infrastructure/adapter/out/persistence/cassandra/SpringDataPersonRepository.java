package com.facturacion.infrastructure.adapter.out.persistence.cassandra;

import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;

public interface SpringDataPersonRepository extends CassandraRepository<PersonEntity, String> {

    /** Usa el indice secundario personas_rol_idx sobre la columna "rol". */
    List<PersonEntity> findByRole(String role);
}
