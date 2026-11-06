package com.facturacion.infrastructure.adapter.in.rest;

import com.facturacion.application.port.in.CreateInvoiceUseCase;
import com.facturacion.application.port.in.GetInvoicesUseCase;
import com.facturacion.domain.model.Invoice;
import com.facturacion.infrastructure.adapter.in.rest.dto.InvoiceDtos.CreateInvoiceRequest;
import com.facturacion.infrastructure.adapter.in.rest.dto.InvoiceDtos.InvoiceConfigResponse;
import com.facturacion.infrastructure.adapter.in.rest.dto.InvoiceDtos.InvoiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador de entrada REST para la facturacion (MySQL + backup). */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final CreateInvoiceUseCase createInvoice;
    private final GetInvoicesUseCase getInvoices;

    public InvoiceController(CreateInvoiceUseCase createInvoice, GetInvoicesUseCase getInvoices) {
        this.createInvoice = createInvoice;
        this.getInvoices = getInvoices;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
        return InvoiceResponse.fromDomain(createInvoice.createInvoice(request.toCommand()));
    }

    /** Tarifa de IVA vigente, para el calculo en vivo del formulario. */
    @GetMapping("/config")
    public InvoiceConfigResponse config() {
        return new InvoiceConfigResponse(createInvoice.currentTaxRate());
    }

    @GetMapping
    public List<InvoiceResponse> list(@RequestParam(required = false) String clientId) {
        List<Invoice> invoices = clientId == null || clientId.isBlank()
                ? getInvoices.findAll()
                : getInvoices.findByClient(clientId);
        return invoices.stream().map(InvoiceResponse::fromDomain).toList();
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable Long id) {
        return InvoiceResponse.fromDomain(getInvoices.findById(id));
    }
}
