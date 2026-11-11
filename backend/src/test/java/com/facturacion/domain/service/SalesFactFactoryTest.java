package com.facturacion.domain.service;

import com.facturacion.domain.exception.DomainException;
import com.facturacion.domain.model.DocumentType;
import com.facturacion.domain.model.Invoice;
import com.facturacion.domain.model.InvoiceDetail;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.PersonRole;
import com.facturacion.domain.model.PersonType;
import com.facturacion.domain.model.Product;
import com.facturacion.domain.model.SalesFact;
import com.facturacion.domain.model.Supplier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalesFactFactoryTest {

    private final Person client = new Person("100", DocumentType.CC, PersonType.NATURAL, "Ana", "a@x.com",
            "", "", "Cali", PersonRole.CLIENTE, null);
    private final Product mouse = new Product("p1", "MOU", "Mouse", "", new BigDecimal("30000"), new BigDecimal("33333"),
            10, true, new Supplier("900", "Proveedor SAS", "", ""), List.of());

    private Invoice invoice() {
        Invoice created = Invoice.create("100", "Ana", List.of(
                new InvoiceDetail("p1", "Mouse", 1, new BigDecimal("33333")),
                new InvoiceDetail("p1b", "Mouse 2", 2, new BigDecimal("33333"))),
                LocalDateTime.of(2026, 11, 5, 10, 0), new BigDecimal("0.19"));
        return Invoice.restore(7L, created.getClientId(), created.getClientName(), created.getIssuedAt(),
                created.getDetails(), created.getSubtotal(), created.getTaxRate(), created.getTaxAmount(), created.getTotal());
    }

    @Test
    void transformaYCuadraElIvaPorLineaConRedondeo() {
        Invoice invoice = invoice();
        List<SalesFact> facts = SalesFactFactory.fromInvoice(invoice, client, Map.of("p1", mouse));

        assertEquals(2, facts.size());
        SalesFact first = facts.get(0);
        assertEquals("FAC-000007", first.invoiceNumber());
        assertEquals("Cali", first.clientCity());
        assertEquals("Proveedor SAS", first.supplierName());
        assertEquals(0, new BigDecimal("3333").compareTo(first.lineMargin()), "margen = 33.333 - 30.000");
        // Producto sin datos en Mongo: se marca como desconocido pero no se pierde la venta
        assertEquals("DESCONOCIDO", facts.get(1).supplierName());

        BigDecimal tax = facts.stream().map(SalesFact::lineTax).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = facts.stream().map(SalesFact::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, invoice.getTaxAmount().compareTo(tax), "el IVA repartido suma el IVA de la factura");
        assertEquals(0, invoice.getTotal().compareTo(total));
    }

    @Test
    void rechazaHechosQueNoCuadranConLaFactura() {
        Invoice invoice = invoice();
        List<SalesFact> facts = SalesFactFactory.fromInvoice(invoice, client, Map.of("p1", mouse));
        assertThrows(DomainException.class, () -> SalesFactFactory.validate(invoice, facts.subList(0, 1)));
    }
}
