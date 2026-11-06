package com.facturacion.infrastructure.adapter.out.persistence.mongo;

import com.facturacion.domain.model.Product;
import com.facturacion.domain.model.Supplier;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Documento de la coleccion "productos". El proveedor va embebido como subdocumento. */
@Document(collection = "productos")
public class ProductDocument {

    @Id
    private String id;

    @Indexed(name = "codigo_1", unique = true)
    @Field("codigo")
    private String code;

    @Field("nombre")
    private String name;

    @Field("descripcion")
    private String description;

    @Field(name = "costo", targetType = FieldType.DECIMAL128)
    private BigDecimal cost;

    @Field(name = "precioVenta", targetType = FieldType.DECIMAL128)
    private BigDecimal salePrice;

    @Field("stock")
    private Integer stock;

    @Field("activo")
    private Boolean active;

    @Field("proveedor")
    private SupplierDocument supplier;

    @Field("imagenes")
    private List<String> images;

    @Field("fechaActualizacion")
    private Instant updatedAt;

    protected ProductDocument() {
    }

    static ProductDocument fromDomain(Product product) {
        ProductDocument doc = new ProductDocument();
        doc.id = product.id();
        doc.code = product.code();
        doc.name = product.name();
        doc.description = product.description();
        doc.cost = product.cost();
        doc.salePrice = product.salePrice();
        doc.stock = product.stock();
        doc.active = product.active();
        doc.supplier = SupplierDocument.fromDomain(product.supplier());
        doc.images = product.imageUrls();
        doc.updatedAt = Instant.now();
        return doc;
    }

    Product toDomain() {
        return new Product(
                id,
                code,
                name,
                description,
                cost == null ? BigDecimal.ZERO : cost,
                salePrice,
                stock == null ? 0 : stock,
                active == null || active,
                supplier == null ? null : supplier.toDomain(),
                images);
    }

    /** Subdocumento embebido "proveedor". */
    public static class SupplierDocument {

        @Field("nit")
        private String document;

        @Field("nombre")
        private String name;

        @Field("correo")
        private String email;

        @Field("telefono")
        private String phone;

        protected SupplierDocument() {
        }

        static SupplierDocument fromDomain(Supplier supplier) {
            if (supplier == null) {
                return null;
            }
            SupplierDocument doc = new SupplierDocument();
            doc.document = supplier.document();
            doc.name = supplier.name();
            doc.email = supplier.email();
            doc.phone = supplier.phone();
            return doc;
        }

        Supplier toDomain() {
            return new Supplier(document, name, email, phone);
        }
    }
}
