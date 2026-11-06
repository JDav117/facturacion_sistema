package com.facturacion.infrastructure.adapter.out.backup.couchdb;

import com.facturacion.application.port.out.InvoiceBackupRepositoryPort;
import com.facturacion.domain.model.BackupAnalytics;
import com.facturacion.domain.model.Invoice;
import com.facturacion.domain.model.InvoiceBackup;
import com.facturacion.infrastructure.config.AsyncConfig;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Adaptador de salida hacia Apache CouchDB (instalado en WSL) que actua como motor analitico de backup.
 *
 * - backup() es @Async: corre en un hilo "proceso-async-N" y la respuesta HTTP de la factura no lo espera.
 * - Cada factura se guarda como un documento JSON con id "factura-{id}" (idempotente).
 * - Crea automaticamente la base y un design document con vistas MapReduce para analitica / ML.
 * - Si CouchDB no responde, el documento se guarda en backup-pendiente/facturas.jsonl para no perder datos.
 */
@Component
public class CouchDbInvoiceBackupAdapter implements InvoiceBackupRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(CouchDbInvoiceBackupAdapter.class);
    private static final String DESIGN_DOC = "_design/analitica";
    private static final String DOC_TYPE = "invoice_backup";

    private final CouchDbProperties props;
    private final RestClient client;
    private final ObjectMapper mapper;
    private volatile boolean initialized = false;

    public CouchDbInvoiceBackupAdapter(CouchDbProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = builder
                .baseUrl(props.url())
                .requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(props.username(), props.password()))
                .build();
    }

    // ------------------------------------------------------------------ backup (escritura asincrona)

    @Async(AsyncConfig.EXECUTOR)
    @Override
    public CompletableFuture<Boolean> backup(Invoice invoice) {
        String thread = Thread.currentThread().getName();
        Map<String, Object> document = toDocument(InvoiceBackup.from(invoice, Instant.now()));
        String docId = "factura-" + invoice.getId();
        try {
            ensureDatabase();
            putDocument(docId, document);
            log.info("[{}] Backup de {} replicado en CouchDB ({}/{})", thread, invoice.number(), props.database(), docId);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("[{}] CouchDB no disponible ({}). {} se guarda en respaldo local pendiente.",
                    thread, e.getMessage(), invoice.number());
            writeFallback(document);
            return CompletableFuture.completedFuture(false);
        }
    }

    private void putDocument(String docId, Map<String, Object> document) {
        try {
            client.put().uri("/{db}/{id}", props.database(), docId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(document)
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict conflict) {
            // Ya existia (por ejemplo, se reinicio MySQL y se repitio el id): se sobrescribe con la revision actual.
            JsonNode current = client.get().uri("/{db}/{id}", props.database(), docId).retrieve().body(JsonNode.class);
            document.put("_rev", current.get("_rev").asText());
            client.put().uri("/{db}/{id}", props.database(), docId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(document)
                    .retrieve().toBodilessEntity();
        }
    }

    private Map<String, Object> toDocument(InvoiceBackup backup) {
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("line_subtotals", backup.lineSubtotals());
        totals.put("subtotal", backup.subtotal());
        totals.put("tax", backup.taxAmount());
        totals.put("invoice_total", backup.invoiceTotal());

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", DOC_TYPE);
        doc.put("invoice_id", backup.invoiceId());
        doc.put("client_id", backup.clientId());
        doc.put("product_ids", backup.productIds());
        doc.put("quantities", backup.quantities());
        doc.put("totals", totals);
        doc.put("timestamp", backup.timestamp().toString());
        doc.put("backed_up_at", backup.backedUpAt().toString());
        doc.put("source", "mysql.facturas");
        return doc;
    }

    private synchronized void writeFallback(Map<String, Object> document) {
        try {
            Path dir = Path.of(props.fallbackDir());
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("facturas.jsonl"), mapper.writeValueAsString(document) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException io) {
            log.error("No se pudo escribir el respaldo local pendiente", io);
        }
    }

    // ------------------------------------------------------------------ inicializacion de la base

    private void ensureDatabase() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            try {
                client.put().uri("/{db}", props.database()).retrieve().toBodilessEntity();
                log.info("Base de datos CouchDB '{}' creada", props.database());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != 412) { // 412 Precondition Failed = la base ya existe
                    throw e;
                }
            }
            try {
                client.put().uri("/{db}/" + DESIGN_DOC, props.database())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(designDocument())
                        .retrieve().toBodilessEntity();
                log.info("Vistas analiticas MapReduce creadas en '{}'", props.database());
            } catch (HttpClientErrorException.Conflict alreadyExists) {
                // 409: el design document ya existe
            }
            initialized = true;
        }
    }

    /** Vistas MapReduce: unidades por producto, ventas por cliente y ventas por dia. */
    private Map<String, Object> designDocument() {
        String filter = "if (doc.type === '" + DOC_TYPE + "') ";
        Map<String, Object> views = new LinkedHashMap<>();
        views.put("unidades_por_producto", Map.of(
                "map", "function (doc) { " + filter + "{ for (var i = 0; i < doc.product_ids.length; i++) { emit(doc.product_ids[i], doc.quantities[i]); } } }",
                "reduce", "_sum"));
        views.put("ventas_por_cliente", Map.of(
                "map", "function (doc) { " + filter + "{ emit(doc.client_id, doc.totals.invoice_total); } }",
                "reduce", "_sum"));
        views.put("ventas_por_dia", Map.of(
                "map", "function (doc) { " + filter + "{ emit(doc.timestamp.substring(0, 10), doc.totals.invoice_total); } }",
                "reduce", "_sum"));
        return Map.of("language", "javascript", "views", views);
    }

    // ------------------------------------------------------------------ consultas

    @Override
    public List<InvoiceBackup> findAll() {
        ensureDatabase();
        JsonNode response = client.get()
                .uri("/{db}/_all_docs?include_docs=true", props.database())
                .retrieve().body(JsonNode.class);
        List<InvoiceBackup> backups = new ArrayList<>();
        for (JsonNode row : response.path("rows")) {
            JsonNode doc = row.path("doc");
            if (DOC_TYPE.equals(doc.path("type").asText())) {
                backups.add(fromDocument(doc));
            }
        }
        return backups;
    }

    @Override
    public BackupAnalytics analytics() {
        ensureDatabase();
        return new BackupAnalytics(
                queryView("unidades_por_producto"),
                queryView("ventas_por_cliente"),
                queryView("ventas_por_dia"));
    }

    private List<BackupAnalytics.Metric> queryView(String view) {
        JsonNode response = client.get()
                .uri("/{db}/" + DESIGN_DOC + "/_view/{view}?group=true", props.database(), view)
                .retrieve().body(JsonNode.class);
        List<BackupAnalytics.Metric> metrics = new ArrayList<>();
        for (JsonNode row : response.path("rows")) {
            metrics.add(new BackupAnalytics.Metric(row.path("key").asText(), row.path("value").decimalValue()));
        }
        metrics.sort((a, b) -> b.value().compareTo(a.value()));
        return metrics;
    }

    private InvoiceBackup fromDocument(JsonNode doc) {
        List<String> productIds = new ArrayList<>();
        doc.path("product_ids").forEach(n -> productIds.add(n.asText()));
        List<Integer> quantities = new ArrayList<>();
        doc.path("quantities").forEach(n -> quantities.add(n.asInt()));
        List<BigDecimal> subtotals = new ArrayList<>();
        doc.path("totals").path("line_subtotals").forEach(n -> subtotals.add(n.decimalValue()));
        JsonNode totals = doc.path("totals");
        BigDecimal invoiceTotal = totals.path("invoice_total").decimalValue();
        // Los backups anteriores al IVA no traen subtotal/tax: subtotal = total, IVA = 0
        BigDecimal subtotal = totals.has("subtotal") ? totals.path("subtotal").decimalValue() : invoiceTotal;
        BigDecimal tax = totals.has("tax") ? totals.path("tax").decimalValue() : BigDecimal.ZERO;
        return new InvoiceBackup(
                doc.path("invoice_id").asLong(),
                doc.path("client_id").asText(),
                productIds,
                quantities,
                subtotals,
                subtotal,
                tax,
                invoiceTotal,
                LocalDateTime.parse(doc.path("timestamp").asText()),
                Instant.parse(doc.path("backed_up_at").asText()));
    }
}
