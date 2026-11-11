package com.facturacion.application.service;

import com.facturacion.application.port.in.CreateInvoiceUseCase.CreateInvoiceCommand;
import com.facturacion.application.port.out.InvoiceBackupRepositoryPort;
import com.facturacion.application.port.out.InvoiceRepositoryPort;
import com.facturacion.application.port.out.PersonRepositoryPort;
import com.facturacion.application.port.out.ProductRepositoryPort;
import com.facturacion.domain.exception.DomainException;
import com.facturacion.domain.exception.ResourceNotFoundException;
import com.facturacion.domain.model.BackupAnalytics;
import com.facturacion.domain.model.DocumentType;
import com.facturacion.domain.model.Invoice;
import com.facturacion.domain.model.InvoiceBackup;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.PersonRole;
import com.facturacion.domain.model.PersonType;
import com.facturacion.domain.model.Product;
import com.facturacion.domain.model.SalesFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba el caso de uso sin Spring ni bases de datos: los puertos se reemplazan por fakes en memoria.
 * Esto es posible gracias a la arquitectura hexagonal.
 */
class InvoiceServiceTest {

    private final Map<String, Product> products = new HashMap<>();
    private final Map<String, Person> persons = new HashMap<>();
    private final List<Invoice> savedInvoices = new ArrayList<>();
    private final List<SalesFact> backedUp = new ArrayList<>();
    private InvoiceService service;

    @BeforeEach
    void setUp() {
        products.put("p1", product("p1", "Mouse", "50000", 10));
        products.put("p2", product("p2", "Teclado", "120000", 2));
        persons.put("100", person("100", PersonRole.CLIENTE));
        persons.put("200", person("200", PersonRole.EMPLEADO));

        Clock clock = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneId.of("America/Bogota"));
        service = new InvoiceService(new FakeInvoices(), new FakeProducts(), new FakePersons(), new FakeBackup(), clock,
                new BigDecimal("0.19"));
    }

    @Test
    void emiteFacturaCalculaTotalDescuentaStockYRespalda() {
        Invoice invoice = service.createInvoice(new CreateInvoiceCommand("100", List.of(
                new CreateInvoiceCommand.Item("p1", 2),
                new CreateInvoiceCommand.Item("p2", 1),
                new CreateInvoiceCommand.Item("p1", 1))));

        assertEquals(1L, invoice.getId());
        assertEquals("FAC-000001", invoice.number());
        assertEquals(2, invoice.getDetails().size(), "las lineas repetidas del mismo producto se agrupan");
        // 3 x 50.000 + 1 x 120.000 = 270.000; IVA 19% = 51.300; total = 321.300
        assertEquals(0, new BigDecimal("270000").compareTo(invoice.getSubtotal()));
        assertEquals(0, new BigDecimal("51300").compareTo(invoice.getTaxAmount()));
        assertEquals(0, new BigDecimal("321300").compareTo(invoice.getTotal()));
        assertEquals(7, products.get("p1").stock());
        assertEquals(1, products.get("p2").stock());
        assertEquals(2, backedUp.size(), "se carga un hecho por linea en el motor de backup");
        BigDecimal factsTotal = backedUp.stream().map(SalesFact::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, invoice.getTotal().compareTo(factsTotal), "los hechos cuadran con la factura");
    }

    @Test
    void rechazaStockInsuficiente() {
        DomainException e = assertThrows(DomainException.class, () -> service.createInvoice(
                new CreateInvoiceCommand("100", List.of(new CreateInvoiceCommand.Item("p2", 5)))));
        assertTrue(e.getMessage().contains("Stock insuficiente"));
        assertTrue(savedInvoices.isEmpty());
        assertTrue(backedUp.isEmpty());
    }

    @Test
    void rechazaClienteInexistenteOConOtroRol() {
        assertThrows(ResourceNotFoundException.class, () -> service.createInvoice(
                new CreateInvoiceCommand("999", List.of(new CreateInvoiceCommand.Item("p1", 1)))));
        assertThrows(DomainException.class, () -> service.createInvoice(
                new CreateInvoiceCommand("200", List.of(new CreateInvoiceCommand.Item("p1", 1)))));
    }

    @Test
    void rechazaProductoInexistente() {
        assertThrows(ResourceNotFoundException.class, () -> service.createInvoice(
                new CreateInvoiceCommand("100", List.of(new CreateInvoiceCommand.Item("nope", 1)))));
    }

    // ------------------------------------------------------------------ fakes

    private static Product product(String id, String name, String price, int stock) {
        return new Product(id, "COD-" + id, name, "", new BigDecimal("1000"), new BigDecimal(price), stock, true, null, List.of());
    }

    private static Person person(String doc, PersonRole role) {
        return new Person(doc, DocumentType.CC, PersonType.NATURAL, "Persona " + doc, "p@test.com", "", "", "", role, null);
    }

    private class FakeInvoices implements InvoiceRepositoryPort {
        public Invoice save(Invoice i) {
            Invoice saved = Invoice.restore((long) savedInvoices.size() + 1, i.getClientId(), i.getClientName(),
                    i.getIssuedAt(), i.getDetails(), i.getSubtotal(), i.getTaxRate(), i.getTaxAmount(), i.getTotal());
            savedInvoices.add(saved);
            return saved;
        }
        public Optional<Invoice> findById(Long id) { return savedInvoices.stream().filter(i -> i.getId().equals(id)).findFirst(); }
        public List<Invoice> findAll() { return savedInvoices; }
        public List<Invoice> findByClientId(String c) { return savedInvoices.stream().filter(i -> i.getClientId().equals(c)).toList(); }
    }

    private class FakeProducts implements ProductRepositoryPort {
        public Product save(Product p) { products.put(p.id(), p); return p; }
        public Optional<Product> findById(String id) { return Optional.ofNullable(products.get(id)); }
        public List<Product> findAll() { return List.copyOf(products.values()); }
        public List<Product> findAvailable() { return findAll(); }
        public CompletableFuture<List<Product>> findAllByIdsAsync(Collection<String> ids) {
            return CompletableFuture.supplyAsync(() -> ids.stream().map(products::get).filter(p -> p != null).toList());
        }
        public boolean decreaseStock(String id, int q) {
            Product p = products.get(id);
            products.put(id, new Product(p.id(), p.code(), p.name(), p.description(), p.cost(), p.salePrice(),
                    p.stock() - q, p.active(), p.supplier(), p.imageUrls()));
            return true;
        }
        public boolean existsById(String id) { return products.containsKey(id); }
        public void deleteById(String id) { products.remove(id); }
    }

    private class FakePersons implements PersonRepositoryPort {
        public Person save(Person p) { persons.put(p.document(), p); return p; }
        public Optional<Person> findByDocument(String d) { return Optional.ofNullable(persons.get(d)); }
        public List<Person> findAll() { return List.copyOf(persons.values()); }
        public List<Person> findByRole(PersonRole r) { return persons.values().stream().filter(p -> p.role() == r).toList(); }
        public CompletableFuture<Optional<Person>> findByDocumentAsync(String d) {
            return CompletableFuture.supplyAsync(() -> findByDocument(d));
        }
        public boolean existsByDocument(String d) { return persons.containsKey(d); }
        public void deleteByDocument(String d) { persons.remove(d); }
    }

    private class FakeBackup implements InvoiceBackupRepositoryPort {
        public CompletableFuture<Boolean> backup(List<SalesFact> facts) { backedUp.addAll(facts); return CompletableFuture.completedFuture(true); }
        public String replaceAll(List<SalesFact> facts) { backedUp.clear(); backedUp.addAll(facts); return "fake"; }
        public List<InvoiceBackup> findAll() { return List.of(); }
        public BackupAnalytics analytics() { return new BackupAnalytics(List.of(), List.of(), List.of(), List.of()); }
    }
}
