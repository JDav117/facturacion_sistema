package com.facturacion.application.service;

import com.facturacion.application.port.in.GenerateFactsUseCase;
import com.facturacion.application.port.out.InvoiceBackupRepositoryPort;
import com.facturacion.application.port.out.InvoiceRepositoryPort;
import com.facturacion.application.port.out.PersonRepositoryPort;
import com.facturacion.application.port.out.ProductRepositoryPort;
import com.facturacion.domain.exception.DomainException;
import com.facturacion.domain.model.Invoice;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.Product;
import com.facturacion.domain.model.SalesFact;
import com.facturacion.domain.service.SalesFactFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Caso de uso "generar hechos" (ETL completo):
 * Extraccion  -> facturas (MySQL), personas (Cassandra), productos (MongoDB)
 * Transformacion y validacion -> SalesFactFactory (dominio)
 * Carga       -> InvoiceBackupRepositoryPort (Apache Druid)
 */
public class FactGenerationService implements GenerateFactsUseCase {

    private final InvoiceRepositoryPort invoiceRepository;
    private final PersonRepositoryPort personRepository;
    private final ProductRepositoryPort productRepository;
    private final InvoiceBackupRepositoryPort backupRepository;

    public FactGenerationService(InvoiceRepositoryPort invoiceRepository,
                                 PersonRepositoryPort personRepository,
                                 ProductRepositoryPort productRepository,
                                 InvoiceBackupRepositoryPort backupRepository) {
        this.invoiceRepository = invoiceRepository;
        this.personRepository = personRepository;
        this.productRepository = productRepository;
        this.backupRepository = backupRepository;
    }

    @Override
    public FactLoadResult regenerateAll() {
        // Extraccion
        List<Invoice> invoices = invoiceRepository.findAll();
        if (invoices.isEmpty()) {
            throw new DomainException("No hay facturas en MySQL para generar hechos");
        }
        Map<String, Person> persons = personRepository.findAll().stream()
                .collect(Collectors.toMap(Person::document, Function.identity()));
        Map<String, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));

        // Transformacion + validacion
        List<SalesFact> facts = new ArrayList<>();
        for (Invoice invoice : invoices) {
            facts.addAll(SalesFactFactory.fromInvoice(invoice, persons.get(invoice.getClientId()), products));
        }

        // Carga
        String loadId = backupRepository.replaceAll(facts);
        return new FactLoadResult(invoices.size(), facts.size(), loadId);
    }
}
