package com.facturacion.domain.service;

import com.facturacion.domain.exception.DomainException;
import com.facturacion.domain.model.Invoice;
import com.facturacion.domain.model.InvoiceDetail;
import com.facturacion.domain.model.Person;
import com.facturacion.domain.model.Product;
import com.facturacion.domain.model.SalesFact;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servicio de dominio puro que implementa la "T" y la "V" del caso de uso generar hechos:
 *
 * - Transformacion: cruza factura (MySQL) + cliente (Cassandra) + producto (MongoDB) en filas planas,
 *   reparte el IVA por linea y calcula el margen (subtotal - costo).
 * - Validacion: cantidades positivas y que la suma de los hechos cuadre exactamente con la factura.
 */
public final class SalesFactFactory {

    private static final String UNKNOWN = "DESCONOCIDO";

    private SalesFactFactory() {
    }

    public static List<SalesFact> fromInvoice(Invoice invoice, Person client, Map<String, Product> products) {
        if (invoice.getId() == null) {
            throw new DomainException("Solo se generan hechos de facturas ya registradas");
        }
        List<InvoiceDetail> details = invoice.getDetails();
        List<SalesFact> facts = new ArrayList<>(details.size());
        BigDecimal taxAssigned = BigDecimal.ZERO;

        for (int i = 0; i < details.size(); i++) {
            InvoiceDetail detail = details.get(i);
            Product product = products.get(detail.productId());
            BigDecimal subtotal = detail.subtotal();

            // El IVA se reparte por linea; la ultima absorbe la diferencia de redondeo para que cuadre.
            BigDecimal lineTax = (i == details.size() - 1)
                    ? invoice.getTaxAmount().subtract(taxAssigned)
                    : Invoice.calculateTax(subtotal, invoice.getTaxRate());
            taxAssigned = taxAssigned.add(lineTax);

            BigDecimal unitCost = product == null ? BigDecimal.ZERO : product.cost();
            BigDecimal margin = subtotal.subtract(unitCost.multiply(BigDecimal.valueOf(detail.quantity())));

            facts.add(new SalesFact(
                    invoice.getIssuedAt(),
                    invoice.getId(),
                    invoice.number(),
                    invoice.getClientId(),
                    client != null ? client.fullName() : invoice.getClientName(),
                    client != null ? client.personType().name() : UNKNOWN,
                    client != null && client.city() != null && !client.city().isBlank() ? client.city() : UNKNOWN,
                    detail.productId(),
                    product != null ? product.code() : UNKNOWN,
                    detail.productName(),
                    product != null && product.supplier() != null ? product.supplier().document() : UNKNOWN,
                    product != null && product.supplier() != null ? product.supplier().name() : UNKNOWN,
                    detail.quantity(),
                    detail.unitPrice(),
                    unitCost,
                    subtotal,
                    lineTax,
                    subtotal.add(lineTax),
                    margin));
        }
        validate(invoice, facts);
        return facts;
    }

    /** Los hechos deben representar exactamente la factura de origen. */
    public static void validate(Invoice invoice, List<SalesFact> facts) {
        if (facts.isEmpty()) {
            throw new DomainException("La factura " + invoice.number() + " no genero hechos");
        }
        for (SalesFact fact : facts) {
            if (fact.quantity() <= 0 || fact.lineSubtotal().signum() < 0) {
                throw new DomainException("Hecho invalido en " + invoice.number() + ": " + fact.productName());
            }
        }
        BigDecimal factsTotal = facts.stream().map(SalesFact::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (factsTotal.compareTo(invoice.getTotal()) != 0) {
            throw new DomainException("Los hechos de " + invoice.number() + " suman " + factsTotal
                    + " pero la factura totaliza " + invoice.getTotal());
        }
    }
}
