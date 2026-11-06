package com.facturacion.application.port.in;

import com.facturacion.domain.model.Invoice;

import java.math.BigDecimal;
import java.util.List;

/** Puerto de entrada: emitir una factura. */
public interface CreateInvoiceUseCase {

    Invoice createInvoice(CreateInvoiceCommand command);

    /** Tarifa de IVA vigente (ej. 0.19), para que la interfaz calcule la vista previa. */
    BigDecimal currentTaxRate();

    /** Datos minimos que envia el cliente: quien compra y que productos/cantidades. */
    record CreateInvoiceCommand(String clientId, List<Item> items) {
        public record Item(String productId, int quantity) {
        }
    }
}
