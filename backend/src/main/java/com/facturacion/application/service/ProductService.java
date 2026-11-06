package com.facturacion.application.service;

import com.facturacion.application.port.in.GetProductsUseCase;
import com.facturacion.application.port.in.ManageProductsUseCase;
import com.facturacion.application.port.out.ProductRepositoryPort;
import com.facturacion.domain.exception.ResourceNotFoundException;
import com.facturacion.domain.model.Product;

import java.util.List;

public class ProductService implements GetProductsUseCase, ManageProductsUseCase {

    private final ProductRepositoryPort productRepository;

    public ProductService(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public List<Product> getCatalog() {
        return productRepository.findAll();
    }

    @Override
    public List<Product> getAvailableCatalog() {
        return productRepository.findAvailable();
    }

    @Override
    public Product getById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el producto " + id));
    }

    @Override
    public boolean isAvailable(String id, int quantity) {
        return getById(id).isAvailable(quantity);
    }

    @Override
    public Product create(Product product) {
        return productRepository.save(product.withId(null));
    }

    @Override
    public Product update(String id, Product product) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("No existe el producto " + id);
        }
        return productRepository.save(product.withId(id));
    }

    @Override
    public void delete(String id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("No existe el producto " + id);
        }
        productRepository.deleteById(id);
    }
}
