package com.facturacion.application.port.in;

import com.facturacion.domain.model.BackupAnalytics;
import com.facturacion.domain.model.InvoiceBackup;

import java.util.List;

/** Puerto de entrada: consultar el backup analitico de facturas. */
public interface GetBackupsUseCase {

    List<InvoiceBackup> findAll();

    BackupAnalytics analytics();
}
