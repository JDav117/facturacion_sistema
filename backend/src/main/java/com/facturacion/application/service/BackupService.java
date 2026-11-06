package com.facturacion.application.service;

import com.facturacion.application.port.in.GetBackupsUseCase;
import com.facturacion.application.port.out.InvoiceBackupRepositoryPort;
import com.facturacion.domain.model.BackupAnalytics;
import com.facturacion.domain.model.InvoiceBackup;

import java.util.Comparator;
import java.util.List;

public class BackupService implements GetBackupsUseCase {

    private final InvoiceBackupRepositoryPort backupRepository;

    public BackupService(InvoiceBackupRepositoryPort backupRepository) {
        this.backupRepository = backupRepository;
    }

    @Override
    public List<InvoiceBackup> findAll() {
        return backupRepository.findAll().stream()
                .sorted(Comparator.comparing(InvoiceBackup::invoiceId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    public BackupAnalytics analytics() {
        return backupRepository.analytics();
    }
}
