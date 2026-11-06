package com.facturacion.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Estructura analitica que se replica al motor de backup / Machine Learning.
 * Solo contiene IDs y montos: invoice_id, client_id, product_ids, quantities, totals y timestamp.
 */
public record InvoiceBackup(
        Long invoiceId,
        String clientId,
        List<String> productIds,
        List<Integer> quantities,
        List<BigDecimal> lineSubtotals,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal invoiceTotal,
        LocalDateTime timestamp,
        Instant backedUpAt) {

    public static InvoiceBackup from(Invoice invoice, Instant backedUpAt) {
        return new InvoiceBackup(
                invoice.getId(),
                invoice.getClientId(),
                invoice.getDetails().stream().map(InvoiceDetail::productId).toList(),
                invoice.getDetails().stream().map(InvoiceDetail::quantity).toList(),
                invoice.getDetails().stream().map(InvoiceDetail::subtotal).toList(),
                invoice.getSubtotal(),
                invoice.getTaxAmount(),
                invoice.getTotal(),
                invoice.getIssuedAt(),
                backedUpAt);
    }
}
