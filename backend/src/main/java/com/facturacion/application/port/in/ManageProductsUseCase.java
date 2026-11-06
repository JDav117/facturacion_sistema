package com.facturacion.application.port.in;

import com.facturacion.domain.model.Product;

/** Puerto de entrada: crear, actualizar y eliminar productos del catalogo. */
public interface ManageProductsUseCase {

    Product create(Product product);

    Product update(String id, Product product);

    void delete(String id);
}
