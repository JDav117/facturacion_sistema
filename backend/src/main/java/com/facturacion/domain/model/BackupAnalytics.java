package com.facturacion.domain.model;

import java.math.BigDecimal;
import java.util.List;

/** Indicadores agregados calculados por el motor analitico (Apache Druid) sobre la tabla de hechos. */
public record BackupAnalytics(
        List<Metric> unitsByProduct,
        List<Metric> salesByClient,
        List<Metric> salesByDay,
        List<Metric> marginBySupplier) {

    public record Metric(String key, BigDecimal value) {
    }
}
