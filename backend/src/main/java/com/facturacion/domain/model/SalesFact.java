package com.facturacion.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hecho de venta: una fila de la tabla de hechos analitica (una por linea de factura).
 * Esta desnormalizado a proposito: une datos de MySQL (factura), Cassandra (cliente)
 * y MongoDB (producto/proveedor) para que el motor analitico no necesite JOINs.
 */
public record SalesFact(
        LocalDateTime timestamp,
        Long invoiceId,
        String invoiceNumber,
        String clientId,
        String clientName,
        String clientType,
        String clientCity,
        String productId,
        String productCode,
        String productName,
        String supplierId,
        String supplierName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal unitCost,
        BigDecimal lineSubtotal,
        BigDecimal lineTax,
        BigDecimal lineTotal,
        BigDecimal lineMargin) {
}
