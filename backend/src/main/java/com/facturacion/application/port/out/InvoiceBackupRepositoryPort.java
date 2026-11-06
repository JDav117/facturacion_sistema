package com.facturacion.application.port.out;

import com.facturacion.domain.model.BackupAnalytics;
import com.facturacion.domain.model.Invoice;
import com.facturacion.domain.model.InvoiceBackup;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Puerto de salida (SPI) hacia el motor analitico de backup / ML.
 * Implementado por el adaptador Apache CouchDB que corre en WSL.
 */
public interface InvoiceBackupRepositoryPort {

    /**
     * Replica la estructura analitica de la factura. Es asincrono: el llamador no espera
     * el resultado (true = replicado en el motor, false = guardado en el respaldo local pendiente).
     */
    CompletableFuture<Boolean> backup(Invoice invoice);

    List<InvoiceBackup> findAll();

    BackupAnalytics analytics();
}
