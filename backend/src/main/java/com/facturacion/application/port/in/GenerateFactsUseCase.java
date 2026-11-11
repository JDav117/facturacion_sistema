package com.facturacion.application.port.in;

/**
 * Puerto de entrada: generar hechos (ETL completo).
 * Extrae de MySQL, Cassandra y MongoDB, transforma y valida, y carga en el motor analitico.
 * (La carga incremental por factura ocurre sola al emitir cada factura.)
 */
public interface GenerateFactsUseCase {

    FactLoadResult regenerateAll();

    record FactLoadResult(int invoices, int facts, String loadId) {
    }
}
