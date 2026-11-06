package com.facturacion.infrastructure.adapter.out.persistence.mysql;

import com.facturacion.domain.model.Invoice;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tabla "facturas". cliente_id guarda el documento de la persona en Cassandra:
 * es una referencia logica, sin llave foranea fisica (vive en otro motor).
 */
@Entity
@Table(name = "facturas", indexes = {
        @Index(name = "idx_facturas_cliente", columnList = "cliente_id"),
        @Index(name = "idx_facturas_fecha", columnList = "fecha")
})
public class InvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false, length = 30)
    private String clientId;

    @Column(name = "cliente_nombre", nullable = false, length = 150)
    private String clientName;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "porcentaje_iva", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "iva", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InvoiceDetailEntity> details = new ArrayList<>();

    protected InvoiceEntity() {
    }

    static InvoiceEntity fromDomain(Invoice invoice) {
        InvoiceEntity entity = new InvoiceEntity();
        entity.id = invoice.getId();
        entity.clientId = invoice.getClientId();
        entity.clientName = invoice.getClientName();
        entity.issuedAt = invoice.getIssuedAt();
        entity.subtotal = invoice.getSubtotal();
        entity.taxRate = invoice.getTaxRate();
        entity.taxAmount = invoice.getTaxAmount();
        entity.total = invoice.getTotal();
        invoice.getDetails().forEach(detail -> entity.details.add(InvoiceDetailEntity.fromDomain(detail, entity)));
        return entity;
    }

    Invoice toDomain() {
        return Invoice.restore(id, clientId, clientName, issuedAt,
                details.stream().map(InvoiceDetailEntity::toDomain).toList(), subtotal, taxRate, taxAmount, total);
    }
}
