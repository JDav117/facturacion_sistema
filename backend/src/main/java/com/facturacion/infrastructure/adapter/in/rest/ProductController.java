package com.facturacion.infrastructure.adapter.in.rest;

import com.facturacion.application.port.in.GetProductsUseCase;
import com.facturacion.application.port.in.ManageProductsUseCase;
import com.facturacion.domain.model.Product;
import com.facturacion.infrastructure.adapter.in.rest.dto.ProductDtos.AvailabilityResponse;
import com.facturacion.infrastructure.adapter.in.rest.dto.ProductDtos.ProductRequest;
import com.facturacion.infrastructure.adapter.in.rest.dto.ProductDtos.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador de entrada REST para el catalogo (MongoDB). */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final GetProductsUseCase getProducts;
    private final ManageProductsUseCase manageProducts;

    public ProductController(GetProductsUseCase getProducts, ManageProductsUseCase manageProducts) {
        this.getProducts = getProducts;
        this.manageProducts = manageProducts;
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(defaultValue = "false") boolean onlyAvailable) {
        List<Product> products = onlyAvailable ? getProducts.getAvailableCatalog() : getProducts.getCatalog();
        return products.stream().map(ProductResponse::fromDomain).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable String id) {
        return ProductResponse.fromDomain(getProducts.getById(id));
    }

    @GetMapping("/{id}/availability")
    public AvailabilityResponse availability(@PathVariable String id, @RequestParam(defaultValue = "1") int quantity) {
        Product product = getProducts.getById(id);
        return new AvailabilityResponse(id, quantity, product.stock(), product.isAvailable(quantity));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return ProductResponse.fromDomain(manageProducts.create(request.toDomain()));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.fromDomain(manageProducts.update(id, request.toDomain()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        manageProducts.delete(id);
    }
}
