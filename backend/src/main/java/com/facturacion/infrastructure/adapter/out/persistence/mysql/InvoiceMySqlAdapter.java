package com.facturacion.infrastructure.adapter.out.persistence.mysql;

import com.facturacion.application.port.out.InvoiceRepositoryPort;
import com.facturacion.domain.model.Invoice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Adaptador de salida: implementa InvoiceRepositoryPort sobre MySQL (JPA). */
@Component
public class InvoiceMySqlAdapter implements InvoiceRepositoryPort {

    private final SpringDataInvoiceRepository repository;

    public InvoiceMySqlAdapter(SpringDataInvoiceRepository repository) {
        this.repository = repository;
    }

    /** Factura y detalles se guardan en la misma transaccion: o se guarda todo o nada. */
    @Transactional
    @Override
    public Invoice save(Invoice invoice) {
        return repository.save(InvoiceEntity.fromDomain(invoice)).toDomain();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<Invoice> findById(Long id) {
        return repository.findByIdWithDetails(id).map(InvoiceEntity::toDomain);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Invoice> findAll() {
        return repository.findAllWithDetails().stream().map(InvoiceEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<Invoice> findByClientId(String clientId) {
        return repository.findByClientIdWithDetails(clientId).stream().map(InvoiceEntity::toDomain).toList();
    }
}
