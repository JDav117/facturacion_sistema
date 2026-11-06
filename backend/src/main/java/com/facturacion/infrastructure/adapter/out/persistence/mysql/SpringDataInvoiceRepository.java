package com.facturacion.infrastructure.adapter.out.persistence.mysql;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataInvoiceRepository extends JpaRepository<InvoiceEntity, Long> {

    @Query("select distinct f from InvoiceEntity f left join fetch f.details order by f.id desc")
    List<InvoiceEntity> findAllWithDetails();

    @Query("select f from InvoiceEntity f left join fetch f.details where f.id = :id")
    Optional<InvoiceEntity> findByIdWithDetails(@Param("id") Long id);

    @Query("select distinct f from InvoiceEntity f left join fetch f.details where f.clientId = :clientId order by f.id desc")
    List<InvoiceEntity> findByClientIdWithDetails(@Param("clientId") String clientId);
}
