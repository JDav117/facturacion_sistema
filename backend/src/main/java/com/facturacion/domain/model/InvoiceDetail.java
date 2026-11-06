package com.facturacion.domain.model;

import com.facturacion.domain.exception.DomainException;

import java.math.BigDecimal;

/**
 * Linea de factura. Guarda una "foto" del nombre y precio del producto al momento de la venta,
 * de modo que la factura no cambia si luego se edita el catalogo en MongoDB.
 */
public record InvoiceDetail(String productId, String productName, int quantity, BigDecimal unitPrice) {

    public InvoiceDetail {
        if (productId == null || productId.isBlank()) {
            throw new DomainException("Cada detalle debe referenciar un producto");
        }
        if (quantity <= 0) {
            throw new DomainException("La cantidad debe ser mayor que cero");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new DomainException("El valor unitario no puede ser negativo");
        }
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
