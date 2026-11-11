package com.facturacion.infrastructure.adapter.in.rest;

import com.facturacion.application.port.in.GenerateFactsUseCase;
import com.facturacion.application.port.in.GenerateFactsUseCase.FactLoadResult;
import com.facturacion.application.port.in.GetBackupsUseCase;
import com.facturacion.domain.model.BackupAnalytics;
import com.facturacion.domain.model.InvoiceBackup;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador de entrada REST para el backup analitico (tabla de hechos en Apache Druid). */
@RestController
@RequestMapping("/api/backups")
public class BackupController {

    private final GetBackupsUseCase getBackups;
    private final GenerateFactsUseCase generateFacts;

    public BackupController(GetBackupsUseCase getBackups, GenerateFactsUseCase generateFacts) {
        this.getBackups = getBackups;
        this.generateFacts = generateFacts;
    }

    @GetMapping
    public List<InvoiceBackup> list() {
        return getBackups.findAll();
    }

    @GetMapping("/analytics")
    public BackupAnalytics analytics() {
        return getBackups.analytics();
    }

    /** Caso de uso "generar hechos": ETL completo MySQL + Cassandra + MongoDB -> Druid. */
    @PostMapping("/regenerate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FactLoadResult regenerate() {
        return generateFacts.regenerateAll();
    }
}
