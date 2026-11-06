package com.facturacion.application.port.in;

import com.facturacion.domain.model.Product;

import java.util.List;

/** Puerto de entrada: consultar el catalogo. */
public interface GetProductsUseCase {

    List<Product> getCatalog();

    List<Product> getAvailableCatalog();

    Product getById(String id);

    boolean isAvailable(String id, int quantity);
}
