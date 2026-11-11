package com.facturacion.application.port.out;

import com.facturacion.domain.model.BackupAnalytics;
import com.facturacion.domain.model.InvoiceBackup;
import com.facturacion.domain.model.SalesFact;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Puerto de salida (SPI) hacia el motor analitico de backup / ML.
 * Implementado por el adaptador de ingesta de Apache Druid que corre en WSL2.
 */
public interface InvoiceBackupRepositoryPort {

    /**
     * Agrega los hechos de una factura recien emitida. Es asincrono: el llamador no espera
     * (true = cargados en el motor, false = guardados en el respaldo local pendiente).
     */
    CompletableFuture<Boolean> backup(List<SalesFact> facts);

    /** Reemplaza la tabla de hechos con una carga completa. Devuelve el identificador de la carga. */
    String replaceAll(List<SalesFact> facts);

    List<InvoiceBackup> findAll();

    BackupAnalytics analytics();
}
