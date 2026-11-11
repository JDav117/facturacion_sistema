package com.facturacion.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vista resumida, por factura, de lo que hay en el motor de backup:
 * invoice_id, client_id, product_ids, quantities, totals y timestamp.
 * Se reconstruye agrupando los hechos de venta de esa factura.
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
}
