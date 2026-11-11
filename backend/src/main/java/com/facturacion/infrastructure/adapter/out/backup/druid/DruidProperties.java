package com.facturacion.infrastructure.adapter.out.backup.druid;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param url         router de Druid (consola, SQL e ingesta), ej. http://localhost:8888
 * @param datasource  tabla de hechos en Druid
 * @param timeZone    zona horaria de negocio para fechas y agrupaciones por dia
 * @param fallbackDir carpeta local donde se guardan los hechos si Druid no esta disponible
 * @param taskTimeout tiempo maximo de espera de una tarea de ingesta
 */
@ConfigurationProperties(prefix = "druid")
public record DruidProperties(String url, String datasource, String timeZone, String fallbackDir, Duration taskTimeout) {
}
