package com.facturacion.application.port.out;

import com.facturacion.domain.model.Invoice;

import java.util.List;
import java.util.Optional;

/** Puerto de salida (SPI) hacia el modulo transaccional. Implementado por el adaptador MySQL. */
public interface InvoiceRepositoryPort {

    /** Persiste factura + detalles en una sola transaccion y devuelve la factura con su ID. */
    Invoice save(Invoice invoice);

    Optional<Invoice> findById(Long id);

    List<Invoice> findAll();

    List<Invoice> findByClientId(String clientId);
}
