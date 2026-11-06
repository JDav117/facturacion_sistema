package com.facturacion.domain.model;

/**
 * Proveedor embebido dentro del producto (se guarda como subdocumento en MongoDB).
 * Su documento coincide con el de la persona juridica registrada en Cassandra.
 */
public record Supplier(String document, String name, String email, String phone) {
}
