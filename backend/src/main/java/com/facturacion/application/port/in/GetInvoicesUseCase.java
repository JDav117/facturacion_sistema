package com.facturacion.application.port.in;

import com.facturacion.domain.model.Invoice;

import java.util.List;

/** Puerto de entrada: consultar facturas emitidas. */
public interface GetInvoicesUseCase {

    List<Invoice> findAll();

    Invoice findById(Long id);

    List<Invoice> findByClient(String clientId);
}
