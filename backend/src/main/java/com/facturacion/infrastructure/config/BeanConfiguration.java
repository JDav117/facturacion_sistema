package com.facturacion.infrastructure.config;

import com.facturacion.application.port.out.InvoiceBackupRepositoryPort;
import com.facturacion.application.port.out.InvoiceRepositoryPort;
import com.facturacion.application.port.out.PersonRepositoryPort;
import com.facturacion.application.port.out.ProductRepositoryPort;
import com.facturacion.application.service.BackupService;
import com.facturacion.application.service.FactGenerationService;
import com.facturacion.application.service.InvoiceService;
import com.facturacion.application.service.PersonService;
import com.facturacion.application.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;

/**
 * Ensambla el hexagono: los servicios de aplicacion son clases Java puras (sin anotaciones de Spring)
 * y aqui se les inyectan los adaptadores que implementan los puertos de salida.
 */
@Configuration
public class BeanConfiguration {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }

    @Bean
    public ProductService productService(ProductRepositoryPort productRepository) {
        return new ProductService(productRepository);
    }

    @Bean
    public PersonService personService(PersonRepositoryPort personRepository, Clock clock) {
        return new PersonService(personRepository, clock);
    }

    @Bean
    public InvoiceService invoiceService(InvoiceRepositoryPort invoiceRepository,
                                         ProductRepositoryPort productRepository,
                                         PersonRepositoryPort personRepository,
                                         InvoiceBackupRepositoryPort backupRepository,
                                         Clock clock,
                                         @Value("${facturacion.iva:0.19}") BigDecimal taxRate) {
        return new InvoiceService(invoiceRepository, productRepository, personRepository, backupRepository, clock, taxRate);
    }

    @Bean
    public FactGenerationService factGenerationService(InvoiceRepositoryPort invoiceRepository,
                                                       PersonRepositoryPort personRepository,
                                                       ProductRepositoryPort productRepository,
                                                       InvoiceBackupRepositoryPort backupRepository) {
        return new FactGenerationService(invoiceRepository, personRepository, productRepository, backupRepository);
    }

    @Bean
    public BackupService backupService(InvoiceBackupRepositoryPort backupRepository) {
        return new BackupService(backupRepository);
    }
}
