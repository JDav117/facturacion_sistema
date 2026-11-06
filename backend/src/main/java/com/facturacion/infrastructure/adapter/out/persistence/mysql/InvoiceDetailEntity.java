package com.facturacion.infrastructure.adapter.out.persistence.mysql;

import com.facturacion.domain.model.InvoiceDetail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Tabla "detalle_factura". producto_id guarda el _id del producto en MongoDB (referencia logica).
 */
@Entity
@Table(name = "detalle_factura")
public class InvoiceDetailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private InvoiceEntity invoice;

    @Column(name = "producto_id", nullable = false, length = 40)
    private String productId;

    @Column(name = "producto_nombre", nullable = false, length = 150)
    private String productName;

    @Column(name = "cantidad", nullable = false)
    private int quantity;

    @Column(name = "valor_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    protected InvoiceDetailEntity() {
    }

    static InvoiceDetailEntity fromDomain(InvoiceDetail detail, InvoiceEntity invoice) {
        InvoiceDetailEntity entity = new InvoiceDetailEntity();
        entity.invoice = invoice;
        entity.productId = detail.productId();
        entity.productName = detail.productName();
        entity.quantity = detail.quantity();
        entity.unitPrice = detail.unitPrice();
        entity.subtotal = detail.subtotal();
        return entity;
    }

    InvoiceDetail toDomain() {
        return new InvoiceDetail(productId, productName, quantity, unitPrice);
    }
}
