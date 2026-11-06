package com.facturacion.infrastructure.adapter.in.rest.dto;

import com.facturacion.application.port.in.CreateInvoiceUseCase.CreateInvoiceCommand;
import com.facturacion.domain.model.Invoice;
import com.facturacion.domain.model.InvoiceDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /** El frontend solo envia cliente, productos y cantidades: precios y totales los calcula el backend. */
    public record CreateInvoiceRequest(
            @NotBlank String clientId,
            @NotEmpty List<@Valid ItemRequest> items) {

        public CreateInvoiceCommand toCommand() {
            return new CreateInvoiceCommand(clientId,
                    items.stream().map(i -> new CreateInvoiceCommand.Item(i.productId(), i.quantity())).toList());
        }
    }

    public record ItemRequest(@NotBlank String productId, @Min(1) int quantity) {
    }

    public record InvoiceDetailResponse(String productId, String productName, int quantity,
                                        BigDecimal unitPrice, BigDecimal subtotal) {
        static InvoiceDetailResponse fromDomain(InvoiceDetail d) {
            return new InvoiceDetailResponse(d.productId(), d.productName(), d.quantity(), d.unitPrice(), d.subtotal());
        }
    }

    public record InvoiceResponse(
            Long id,
            String number,
            String clientId,
            String clientName,
            LocalDateTime issuedAt,
            List<InvoiceDetailResponse> details,
            int itemsCount,
            BigDecimal subtotal,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal total) {

        public static InvoiceResponse fromDomain(Invoice invoice) {
            return new InvoiceResponse(invoice.getId(), invoice.number(), invoice.getClientId(), invoice.getClientName(),
                    invoice.getIssuedAt(), invoice.getDetails().stream().map(InvoiceDetailResponse::fromDomain).toList(),
                    invoice.itemsCount(), invoice.getSubtotal(), invoice.getTaxRate(), invoice.getTaxAmount(),
                    invoice.getTotal());
        }
    }

    public record InvoiceConfigResponse(BigDecimal taxRate) {
    }
}
