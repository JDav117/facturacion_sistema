package com.facturacion.application.service;

import com.facturacion.application.port.in.CreateInvoiceUseCase;
import com.facturacion.application.port.in.GetInvoicesUseCase;
import com.facturacion.application.port.out.InvoiceBackupRepositoryPort;
import com.facturacion.application.port.out.InvoiceRepositoryPort;
import com.facturacion.application.port.out.PersonRepositoryPort;
import com.facturacion.application.port.out.ProductRepositoryPort;
import com.facturacion.domain.exception.DomainException;
import com.facturacion.domain.exception.ResourceNotFoundException;
import com.facturacion.domain.model.Invoice;
import com.facturacion.domain.model.InvoiceDetail;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.Product;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Caso de uso principal: orquesta los cuatro motores sin conocer ninguno.
 *
 * 1. Consulta EN PARALELO el cliente (Cassandra) y los productos (MongoDB) usando los puertos asincronos.
 * 2. Aplica las reglas de negocio (cliente valido, disponibilidad, precio tomado del catalogo, IVA).
 * 3. Persiste la factura en MySQL (transaccion).
 * 4. Descuenta stock en el catalogo.
 * 5. Dispara el backup analitico asincrono (CouchDB) sin bloquear la respuesta.
 */
public class InvoiceService implements CreateInvoiceUseCase, GetInvoicesUseCase {

    private static final System.Logger LOG = System.getLogger(InvoiceService.class.getName());

    private final InvoiceRepositoryPort invoiceRepository;
    private final ProductRepositoryPort productRepository;
    private final PersonRepositoryPort personRepository;
    private final InvoiceBackupRepositoryPort backupRepository;
    private final Clock clock;
    private final BigDecimal taxRate;

    public InvoiceService(InvoiceRepositoryPort invoiceRepository,
                          ProductRepositoryPort productRepository,
                          PersonRepositoryPort personRepository,
                          InvoiceBackupRepositoryPort backupRepository,
                          Clock clock,
                          BigDecimal taxRate) {
        this.invoiceRepository = invoiceRepository;
        this.productRepository = productRepository;
        this.personRepository = personRepository;
        this.backupRepository = backupRepository;
        this.clock = clock;
        this.taxRate = taxRate == null ? Invoice.DEFAULT_TAX_RATE : taxRate;
    }

    @Override
    public BigDecimal currentTaxRate() {
        return taxRate;
    }

    @Override
    public Invoice createInvoice(CreateInvoiceCommand command) {
        Map<String, Integer> quantities = validateAndGroup(command);

        // Ambas consultas arrancan a la vez en hilos del pool; luego esperamos las dos.
        CompletableFuture<Optional<Person>> clientFuture = personRepository.findByDocumentAsync(command.clientId());
        CompletableFuture<List<Product>> productsFuture = productRepository.findAllByIdsAsync(quantities.keySet());

        Person client = await(clientFuture)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el cliente con documento " + command.clientId()));
        if (!client.isClient()) {
            throw new DomainException("La persona " + client.fullName() + " no tiene rol CLIENTE");
        }

        Map<String, Product> products = await(productsFuture).stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));

        List<InvoiceDetail> details = new ArrayList<>();
        quantities.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("No existe el producto " + productId);
            }
            if (!product.active()) {
                throw new DomainException("El producto " + product.name() + " no esta activo");
            }
            if (!product.isAvailable(quantity)) {
                throw new DomainException("Stock insuficiente para " + product.name()
                        + ": disponible " + product.stock() + ", solicitado " + quantity);
            }
            // El precio siempre se toma del catalogo, nunca del cliente.
            details.add(new InvoiceDetail(product.id(), product.name(), quantity, product.salePrice()));
        });

        Invoice invoice = Invoice.create(client.document(), client.fullName(), details, LocalDateTime.now(clock), taxRate);
        Invoice saved = invoiceRepository.save(invoice);
        LOG.log(System.Logger.Level.INFO, "Factura {0} registrada en MySQL por {1}", saved.number(), saved.getTotal());

        quantities.forEach((productId, quantity) -> {
            if (!productRepository.decreaseStock(productId, quantity)) {
                LOG.log(System.Logger.Level.WARNING, "No se pudo descontar stock del producto {0}", productId);
            }
        });

        triggerBackup(saved);
        return saved;
    }

    /** El backup nunca debe tumbar la emision de la factura: corre en otro hilo y se aisla cualquier fallo. */
    private void triggerBackup(Invoice saved) {
        try {
            backupRepository.backup(saved).whenComplete((ok, error) -> {
                if (error != null) {
                    LOG.log(System.Logger.Level.ERROR, "Fallo el backup de la factura " + saved.number(), error);
                }
            });
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "No se pudo lanzar el backup de la factura " + saved.number(), e);
        }
    }

    private Map<String, Integer> validateAndGroup(CreateInvoiceCommand command) {
        if (command == null || command.clientId() == null || command.clientId().isBlank()) {
            throw new DomainException("Debe seleccionar un cliente");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new DomainException("La factura debe tener al menos un producto");
        }
        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (CreateInvoiceCommand.Item item : command.items()) {
            if (item.productId() == null || item.productId().isBlank()) {
                throw new DomainException("Hay un item sin producto");
            }
            if (item.quantity() <= 0) {
                throw new DomainException("La cantidad debe ser mayor que cero");
            }
            grouped.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return grouped;
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    @Override
    public List<Invoice> findAll() {
        return invoiceRepository.findAll();
    }

    @Override
    public Invoice findById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la factura " + id));
    }

    @Override
    public List<Invoice> findByClient(String clientId) {
        return invoiceRepository.findByClientId(clientId);
    }
}
