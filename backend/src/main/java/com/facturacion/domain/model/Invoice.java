package com.facturacion.domain.model;

import com.facturacion.domain.exception.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agregado raiz de la factura. Calcula subtotal, IVA y total a partir de los detalles
 * (sin pagos a credito, segun el alcance del prototipo).
 */
public final class Invoice {

    /** Tarifa general de IVA en Colombia. */
    public static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.19");

    private final Long id;
    private final String clientId;
    private final String clientName;
    private final LocalDateTime issuedAt;
    private final List<InvoiceDetail> details;
    private final BigDecimal subtotal;
    private final BigDecimal taxRate;
    private final BigDecimal taxAmount;
    private final BigDecimal total;

    private Invoice(Long id, String clientId, String clientName, LocalDateTime issuedAt, List<InvoiceDetail> details,
                    BigDecimal subtotal, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal total) {
        this.id = id;
        this.clientId = clientId;
        this.clientName = clientName;
        this.issuedAt = issuedAt;
        this.details = List.copyOf(details);
        this.subtotal = subtotal;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.total = total;
    }

    /** Crea una factura nueva aplicando las reglas de negocio: total = subtotal + IVA. */
    public static Invoice create(String clientId, String clientName, List<InvoiceDetail> details,
                                 LocalDateTime issuedAt, BigDecimal taxRate) {
        if (clientId == null || clientId.isBlank()) {
            throw new DomainException("La factura debe tener un cliente");
        }
        if (details == null || details.isEmpty()) {
            throw new DomainException("La factura debe tener al menos un producto");
        }
        if (issuedAt == null) {
            throw new DomainException("La factura debe tener fecha de emision");
        }
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainException("La tarifa de IVA debe estar entre 0 y 1");
        }
        BigDecimal subtotal = calculateSubtotal(details);
        BigDecimal taxAmount = calculateTax(subtotal, taxRate);
        return new Invoice(null, clientId, clientName, issuedAt, details,
                subtotal, taxRate, taxAmount, subtotal.add(taxAmount));
    }

    /** Reconstruye una factura ya persistida (usado por los adaptadores de salida). */
    public static Invoice restore(Long id, String clientId, String clientName, LocalDateTime issuedAt,
                                  List<InvoiceDetail> details, BigDecimal subtotal, BigDecimal taxRate,
                                  BigDecimal taxAmount, BigDecimal total) {
        return new Invoice(id, clientId, clientName, issuedAt, details, subtotal, taxRate, taxAmount, total);
    }

    public static BigDecimal calculateSubtotal(List<InvoiceDetail> details) {
        return details.stream()
                .map(InvoiceDetail::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** IVA redondeado a centavos (mitad hacia arriba). */
    public static BigDecimal calculateTax(BigDecimal subtotal, BigDecimal taxRate) {
        return subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Numero legible de factura, derivado del ID transaccional de MySQL. */
    public String number() {
        return id == null ? null : "FAC-%06d".formatted(id);
    }

    public int itemsCount() {
        return details.stream().mapToInt(InvoiceDetail::quantity).sum();
    }

    public Long getId() { return id; }
    public String getClientId() { return clientId; }
    public String getClientName() { return clientName; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public List<InvoiceDetail> getDetails() { return details; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotal() { return total; }
}
