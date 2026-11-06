package com.facturacion.infrastructure.adapter.in.rest;

import com.facturacion.application.port.in.GetBackupsUseCase;
import com.facturacion.domain.model.BackupAnalytics;
import com.facturacion.domain.model.InvoiceBackup;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador de entrada REST para consultar el backup analitico (CouchDB). */
@RestController
@RequestMapping("/api/backups")
public class BackupController {

    private final GetBackupsUseCase getBackups;

    public BackupController(GetBackupsUseCase getBackups) {
        this.getBackups = getBackups;
    }

    @GetMapping
    public List<InvoiceBackup> list() {
        return getBackups.findAll();
    }

    @GetMapping("/analytics")
    public BackupAnalytics analytics() {
        return getBackups.analytics();
    }
}
