package com.facturacion.infrastructure.adapter.out.backup.druid;

import com.facturacion.application.port.out.InvoiceBackupRepositoryPort;
import com.facturacion.domain.model.BackupAnalytics;
import com.facturacion.domain.model.InvoiceBackup;
import com.facturacion.domain.model.SalesFact;
import com.facturacion.infrastructure.config.AsyncConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Adaptador de ingesta hacia Apache Druid (WSL2): carga la tabla de hechos "hechos_ventas".
 *
 * - backup(): @Async. Envia una tarea de ingesta nativa (index_parallel, datos inline, append) y espera
 *   su resultado en un hilo "proceso-async-N"; la respuesta HTTP de la factura no la espera.
 * - replaceAll(): carga completa que sobrescribe los segmentos (usada por el caso de uso generar hechos).
 * - Las consultas usan Druid SQL (/druid/v2/sql).
 * - Si Druid no responde, los hechos se guardan en backup-pendiente/hechos.jsonl para no perder datos.
 */
@Component
public class DruidIngestionAdapter implements InvoiceBackupRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(DruidIngestionAdapter.class);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final DruidProperties props;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final ZoneId zone;

    public DruidIngestionAdapter(DruidProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.zone = ZoneId.of(props.timeZone());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = builder.baseUrl(props.url()).requestFactory(factory).build();
    }

    // ------------------------------------------------------------------ carga (ingesta)

    @Async(AsyncConfig.EXECUTOR)
    @Override
    public CompletableFuture<Boolean> backup(List<SalesFact> facts) {
        String thread = Thread.currentThread().getName();
        String invoice = facts.isEmpty() ? "?" : facts.get(0).invoiceNumber();
        long start = System.currentTimeMillis();
        try {
            String taskId = submitIngestion(facts, true);
            log.info("[{}] {} hecho(s) de {} enviados a Druid (tarea {})", thread, facts.size(), invoice, taskId);
            String status = waitForTask(taskId);
            if ("SUCCESS".equals(status)) {
                log.info("[{}] Hechos de {} cargados en Druid '{}' en {} ms", thread, invoice, props.datasource(),
                        System.currentTimeMillis() - start);
                return CompletableFuture.completedFuture(true);
            }
            log.error("[{}] La tarea de ingesta de {} termino en estado {}", thread, invoice, status);
        } catch (Exception e) {
            log.error("[{}] Druid no disponible ({}).", thread, e.getMessage());
        }
        writeFallback(facts);
        log.warn("[{}] Hechos de {} guardados en respaldo local pendiente", thread, invoice);
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public String replaceAll(List<SalesFact> facts) {
        String taskId = submitIngestion(facts, false);
        log.info("Carga completa de {} hecho(s) enviada a Druid (tarea {})", facts.size(), taskId);
        return taskId;
    }

    private String submitIngestion(List<SalesFact> facts, boolean append) {
        JsonNode response = client.post().uri("/druid/indexer/v1/task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ingestionSpec(facts, append))
                .retrieve().body(JsonNode.class);
        return response.path("task").asText();
    }

    private String waitForTask(String taskId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + props.taskTimeout().toMillis();
        while (System.currentTimeMillis() < deadline) {
            JsonNode status = client.get().uri("/druid/indexer/v1/task/{id}/status", taskId)
                    .retrieve().body(JsonNode.class).path("status");
            String code = status.hasNonNull("statusCode") ? status.path("statusCode").asText() : status.path("status").asText();
            if ("SUCCESS".equals(code) || "FAILED".equals(code)) {
                return code;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        return "TIMEOUT";
    }

    /** Especificacion de ingesta nativa por lotes con los hechos en linea (NDJSON). */
    private Map<String, Object> ingestionSpec(List<SalesFact> facts, boolean append) {
        String loadedAt = Instant.now().toString();
        StringBuilder ndjson = new StringBuilder();
        for (SalesFact fact : facts) {
            ndjson.append(toJson(toRow(fact, loadedAt))).append('\n');
        }

        List<Object> dimensions = new ArrayList<>(List.of(
                Map.of("type", "long", "name", "invoice_id"),
                "invoice_number", "client_id", "client_name", "client_type", "client_city",
                "product_id", "product_code", "product_name", "supplier_id", "supplier_name", "loaded_at",
                Map.of("type", "long", "name", "quantity")));
        for (String metric : List.of("unit_price", "unit_cost", "line_subtotal", "line_tax", "line_total", "line_margin")) {
            dimensions.add(Map.of("type", "double", "name", metric));
        }

        Map<String, Object> dataSchema = Map.of(
                "dataSource", props.datasource(),
                "timestampSpec", Map.of("column", "timestamp", "format", "iso"),
                "dimensionsSpec", Map.of("dimensions", dimensions),
                "granularitySpec", Map.of("segmentGranularity", "day", "queryGranularity", "none", "rollup", false));

        Map<String, Object> ioConfig = Map.of(
                "type", "index_parallel",
                "inputSource", Map.of("type", "inline", "data", ndjson.toString()),
                "inputFormat", Map.of("type", "json"),
                "appendToExisting", append);

        Map<String, Object> tuningConfig = Map.of(
                "type", "index_parallel",
                "partitionsSpec", Map.of("type", "dynamic"),
                "maxNumConcurrentSubTasks", 1);

        return Map.of("type", "index_parallel",
                "spec", Map.of("dataSchema", dataSchema, "ioConfig", ioConfig, "tuningConfig", tuningConfig));
    }

    private Map<String, Object> toRow(SalesFact f, String loadedAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("timestamp", f.timestamp().atZone(zone).toInstant().toString());
        row.put("invoice_id", f.invoiceId());
        row.put("invoice_number", f.invoiceNumber());
        row.put("client_id", f.clientId());
        row.put("client_name", f.clientName());
        row.put("client_type", f.clientType());
        row.put("client_city", f.clientCity());
        row.put("product_id", f.productId());
        row.put("product_code", f.productCode());
        row.put("product_name", f.productName());
        row.put("supplier_id", f.supplierId());
        row.put("supplier_name", f.supplierName());
        row.put("quantity", f.quantity());
        row.put("unit_price", f.unitPrice());
        row.put("unit_cost", f.unitCost());
        row.put("line_subtotal", f.lineSubtotal());
        row.put("line_tax", f.lineTax());
        row.put("line_total", f.lineTotal());
        row.put("line_margin", f.lineMargin());
        row.put("loaded_at", loadedAt);
        return row;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private synchronized void writeFallback(List<SalesFact> facts) {
        try {
            Path dir = Path.of(props.fallbackDir());
            Files.createDirectories(dir);
            String loadedAt = Instant.now().toString();
            StringBuilder lines = new StringBuilder();
            facts.forEach(f -> lines.append(toJson(toRow(f, loadedAt))).append(System.lineSeparator()));
            Files.writeString(dir.resolve("hechos.jsonl"), lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException io) {
            log.error("No se pudo escribir el respaldo local pendiente", io);
        }
    }

    // ------------------------------------------------------------------ consultas (Druid SQL)

    @Override
    public List<InvoiceBackup> findAll() {
        JsonNode rows = sql("SELECT __time, invoice_id, client_id, product_id, quantity, line_subtotal, line_tax, "
                + "line_total, loaded_at FROM " + props.datasource() + " ORDER BY __time DESC LIMIT 5000");

        Map<Long, List<JsonNode>> byInvoice = new LinkedHashMap<>();
        rows.forEach(row -> byInvoice.computeIfAbsent(row.path("invoice_id").asLong(), k -> new ArrayList<>()).add(row));

        List<InvoiceBackup> backups = new ArrayList<>();
        byInvoice.forEach((invoiceId, lines) -> {
            JsonNode first = lines.get(0);
            backups.add(new InvoiceBackup(
                    invoiceId,
                    first.path("client_id").asText(),
                    lines.stream().map(l -> l.path("product_id").asText()).toList(),
                    lines.stream().map(l -> l.path("quantity").asInt()).toList(),
                    lines.stream().map(l -> l.path("line_subtotal").decimalValue()).toList(),
                    sum(lines, "line_subtotal"),
                    sum(lines, "line_tax"),
                    sum(lines, "line_total"),
                    LocalDateTime.ofInstant(Instant.parse(first.path("__time").asText()), zone),
                    parseInstant(first.path("loaded_at").asText())));
        });
        return backups;
    }

    @Override
    public BackupAnalytics analytics() {
        String ds = props.datasource();
        String tz = props.timeZone();
        return new BackupAnalytics(
                metrics("SELECT product_name AS k, SUM(quantity) AS v FROM " + ds + " GROUP BY 1 ORDER BY 2 DESC"),
                metrics("SELECT client_name AS k, SUM(line_total) AS v FROM " + ds + " GROUP BY 1 ORDER BY 2 DESC"),
                metrics("SELECT TIME_FORMAT(__time, 'yyyy-MM-dd', '" + tz + "') AS k, "
                        + "SUM(line_total) AS v FROM " + ds + " GROUP BY 1 ORDER BY 1 DESC"),
                metrics("SELECT supplier_name AS k, SUM(line_margin) AS v FROM " + ds + " GROUP BY 1 ORDER BY 2 DESC"));
    }

    private List<BackupAnalytics.Metric> metrics(String query) {
        List<BackupAnalytics.Metric> result = new ArrayList<>();
        sql(query).forEach(row -> result.add(new BackupAnalytics.Metric(row.path("k").asText(), row.path("v").decimalValue())));
        return result;
    }

    /** Ejecuta Druid SQL. Si la tabla de hechos aun no existe (no se ha cargado nada) devuelve vacio. */
    private JsonNode sql(String query) {
        try {
            return client.post().uri("/druid/v2/sql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query))
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            if (e.getResponseBodyAsString().contains("not found")) {
                return mapper.createArrayNode();
            }
            throw e;
        }
    }

    private static BigDecimal sum(List<JsonNode> lines, String field) {
        return lines.stream().map(l -> l.path(field).decimalValue()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
