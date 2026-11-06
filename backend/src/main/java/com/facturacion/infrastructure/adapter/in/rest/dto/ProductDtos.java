package com.facturacion.infrastructure.adapter.in.rest.dto;

import com.facturacion.domain.model.Product;
import com.facturacion.domain.model.Supplier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record SupplierDto(String document, String name, String email, String phone) {
        Supplier toDomain() {
            return new Supplier(document, name, email, phone);
        }

        static SupplierDto fromDomain(Supplier s) {
            return s == null ? null : new SupplierDto(s.document(), s.name(), s.email(), s.phone());
        }
    }

    public record ProductRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotNull @DecimalMin("0") BigDecimal cost,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal salePrice,
            @Min(0) int stock,
            Boolean active,
            @Valid SupplierDto supplier,
            List<String> imageUrls) {

        public Product toDomain() {
            return new Product(null, code.trim(), name.trim(), description, cost, salePrice, stock,
                    active == null || active, supplier == null ? null : supplier.toDomain(), imageUrls);
        }
    }

    public record ProductResponse(
            String id,
            String code,
            String name,
            String description,
            BigDecimal cost,
            BigDecimal salePrice,
            BigDecimal margin,
            int stock,
            boolean active,
            boolean available,
            SupplierDto supplier,
            List<String> imageUrls) {

        public static ProductResponse fromDomain(Product p) {
            return new ProductResponse(p.id(), p.code(), p.name(), p.description(), p.cost(), p.salePrice(),
                    p.margin(), p.stock(), p.active(), p.isAvailable(1), SupplierDto.fromDomain(p.supplier()), p.imageUrls());
        }
    }

    public record AvailabilityResponse(String productId, int requested, int stock, boolean available) {
    }
}
