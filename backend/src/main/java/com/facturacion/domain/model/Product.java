package com.facturacion.domain.model;

import com.facturacion.domain.exception.DomainException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Producto del catalogo. Entidad de dominio pura: valida sus propias reglas al construirse.
 */
public record Product(
        String id,
        String code,
        String name,
        String description,
        BigDecimal cost,
        BigDecimal salePrice,
        int stock,
        boolean active,
        Supplier supplier,
        List<String> imageUrls) {

    public Product {
        if (code == null || code.isBlank()) {
            throw new DomainException("El codigo del producto es obligatorio");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException("El nombre del producto es obligatorio");
        }
        if (cost == null || cost.signum() < 0) {
            throw new DomainException("El costo del producto no puede ser negativo");
        }
        if (salePrice == null || salePrice.signum() <= 0) {
            throw new DomainException("El precio de venta debe ser mayor que cero");
        }
        if (salePrice.compareTo(cost) < 0) {
            throw new DomainException("El precio de venta no puede ser menor que el costo");
        }
        if (stock < 0) {
            throw new DomainException("El stock no puede ser negativo");
        }
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }

    /** Consulta de disponibilidad: el producto esta activo y hay unidades suficientes. */
    public boolean isAvailable(int quantity) {
        return active && quantity > 0 && stock >= quantity;
    }

    public BigDecimal margin() {
        return salePrice.subtract(cost);
    }

    public Product withId(String newId) {
        return new Product(newId, code, name, description, cost, salePrice, stock, active, supplier, imageUrls);
    }
}
