# Sistema de Facturación — Arquitectura Hexagonal

Prototipo de facturación en **Spring Boot 3.4 (Java 21)** + **React 18**, con persistencia distribuida en cuatro motores:

| Motor | Rol en el sistema | Dónde corre |
|---|---|---|
| **MongoDB 7** | Catálogo de productos (costo, precio, imágenes, proveedor embebido) | Docker |
| **Apache Cassandra 4.1** | Personas: clientes, empleados y proveedores (naturales o jurídicas) | Docker |
| **MySQL 8.4** | Parte transaccional: `facturas` + `detalle_factura` | Docker |
| **Apache CouchDB 3** | Backup analítico / ML de cada factura (réplica automática) | **WSL (Ubuntu)** |

---

## 1. Arquitectura

```
                 ┌───────────────────────── ADAPTADORES DE ENTRADA ─────────────────────────┐
  React (5173) ─►│ ProductController  PersonController  InvoiceController  BackupController │
                 │ ConcurrencyDemoRunner (ApplicationRunner → 3 procesos @Async)           │
                 └──────────────────────────────────┬──────────────────────────────────────┘
                                                    │ puertos de entrada (casos de uso)
                 ┌──────────────────────────────────▼──────────────────────────────────────┐
                 │  APLICACIÓN   CreateInvoiceUseCase · GetProductsUseCase · RegisterPerson… │
                 │               InvoiceService · ProductService · PersonService · Backup…   │
                 │  DOMINIO      Product · Person · Invoice · InvoiceDetail (Java puro)      │
                 └──────────────────────────────────┬──────────────────────────────────────┘
                                                    │ puertos de salida (SPI)
                 ┌──────────────┬───────────────────┼──────────────────┬───────────────────┐
                 │ProductMongo  │ PersonCassandra    │ InvoiceMySql     │ CouchDbInvoice    │
                 │Adapter       │ Adapter            │ Adapter          │ BackupAdapter     │
                 └──────┬───────┴─────────┬──────────┴────────┬─────────┴─────────┬─────────┘
                     MongoDB          Cassandra             MySQL          CouchDB (WSL)
```

- El **dominio** y la **aplicación** no importan nada de Spring ni de ninguna base de datos. Los servicios se ensamblan en [BeanConfiguration.java](backend/src/main/java/com/facturacion/infrastructure/config/BeanConfiguration.java).
- Prueba de ello: [InvoiceServiceTest.java](backend/src/test/java/com/facturacion/application/service/InvoiceServiceTest.java) ejecuta el caso de uso completo con puertos falsos en memoria, sin levantar ninguna base.

### Estructura de paquetes

```
backend/src/main/java/com/facturacion/
├── domain/
│   ├── model/        Product, Supplier, Person, Invoice, InvoiceDetail, InvoiceBackup, BackupAnalytics, enums
│   └── exception/    DomainException, ResourceNotFoundException
├── application/
│   ├── port/in/      CreateInvoiceUseCase, GetInvoicesUseCase, GetProductsUseCase, ManageProductsUseCase,
│   │                 RegisterPersonUseCase, GetPersonsUseCase, GetBackupsUseCase
│   ├── port/out/     ProductRepositoryPort, PersonRepositoryPort, InvoiceRepositoryPort, InvoiceBackupRepositoryPort
│   └── service/      InvoiceService, ProductService, PersonService, BackupService
└── infrastructure/
    ├── adapter/in/rest/                    Controladores REST + DTOs + GlobalExceptionHandler
    ├── adapter/out/persistence/mongo/      ProductDocument, ProductMongoAdapter
    ├── adapter/out/persistence/cassandra/  PersonEntity, PersonCassandraAdapter
    ├── adapter/out/persistence/mysql/      InvoiceEntity, InvoiceDetailEntity, InvoiceMySqlAdapter
    ├── adapter/out/backup/couchdb/         CouchDbInvoiceBackupAdapter
    ├── concurrency/                        ProcesoInventario, ProcesoReportes, ProcesoNotificaciones, ConcurrencyDemoRunner
    └── config/                             AsyncConfig, BeanConfiguration, PersistenceConfig, WebConfig
```

### Flujo de "Emitir factura" ([InvoiceService.java](backend/src/main/java/com/facturacion/application/service/InvoiceService.java))

1. Consulta **en paralelo** el cliente en Cassandra y los productos en MongoDB (dos métodos `@Async` que devuelven `CompletableFuture`).
2. Valida reglas: el cliente existe y tiene rol CLIENTE, cada producto existe, está activo y tiene stock. **El precio se toma del catálogo**, nunca del navegador. Calcula subtotal, **IVA (19 %, configurable en `facturacion.iva`)** y total.
3. Guarda `facturas` + `detalle_factura` en MySQL en **una sola transacción**.
4. Descuenta stock en MongoDB con una actualización atómica (`stock >= cantidad`).
5. Dispara el **backup asíncrono** a CouchDB en un hilo `proceso-async-N`. La respuesta HTTP no lo espera; si CouchDB está caído, el documento se guarda en `backend/backup-pendiente/facturas.jsonl` y la factura no falla.

---

## 2. Concurrencia (tema de clase: hilos → `@Async`)

| Elemento | Dónde |
|---|---|
| `@EnableAsync` | [FacturacionApplication.java](backend/src/main/java/com/facturacion/FacturacionApplication.java) |
| Bean `ThreadPoolTaskExecutor` **`procesosExecutor`** (core 3, max 10, cola 0, prefijo `proceso-async-`) | [AsyncConfig.java](backend/src/main/java/com/facturacion/infrastructure/config/AsyncConfig.java) |
| `@Async("procesosExecutor")` en adaptadores de persistencia | `ProductMongoAdapter.findAllByIdsAsync`, `PersonCassandraAdapter.findByDocumentAsync`, `CouchDbInvoiceBackupAdapter.backup` |
| `@Component` + `@Async` en procesos | `ProcesoInventario`, `ProcesoReportes`, `ProcesoNotificaciones` |
| `ApplicationRunner` que los lanza al iniciar | [ConcurrencyDemoRunner.java](backend/src/main/java/com/facturacion/infrastructure/concurrency/ConcurrencyDemoRunner.java) |

Al arrancar el backend se ve en consola el intercalado no determinista:

```
[proceso-async-1 ] INVENTARIO      -> paso 1/5
[proceso-async-3 ] NOTIFICACIONES  -> paso 1/5
[proceso-async-2 ] REPORTES        -> paso 1/5
[main            ] ==== Hilo [main] libre: no espera a los procesos ====
[proceso-async-3 ] NOTIFICACIONES  -> paso 2/5
...
```

Y al emitir una factura:

```
[http-nio-8080-exec-3] InvoiceService : Factura FAC-000001 registrada en MySQL ...
[proceso-async-4     ] CouchDbInvoiceBackupAdapter : Backup de FAC-000001 replicado en CouchDB
```

> Con `queueCapacity = 0`, si los 10 hilos están ocupados se usa `CallerRunsPolicy`: la tarea la ejecuta el hilo que la pidió en vez de rechazarse, así no se pierde ningún backup.

---

## 3. Puesta en marcha

### Requisitos
Java 21, Maven 3.9+, Node 18+, Docker Desktop y WSL 2 con Ubuntu.

### Paso 1 — Bases de datos en Docker

```bash
docker compose up -d
docker compose logs -f cassandra-init   # esperar a que termine (Cassandra tarda ~1 min en arrancar)
```

Esto crea y **puebla** automáticamente:
- MySQL → [infra/mysql/01-schema.sql](infra/mysql/01-schema.sql) (puerto **3307** en el host para no chocar con un MySQL local)
- Mongo usa el puerto **27018** en el host por la misma razón
- MongoDB → [infra/mongo/01-productos.js](infra/mongo/01-productos.js) (9 productos con imágenes, costo, precio y proveedor)
- Cassandra → [infra/cassandra/init.cql](infra/cassandra/init.cql) (6 clientes, 2 empleados, 3 proveedores)

### Paso 2 — Motor de backup Apache CouchDB en WSL

Desde Ubuntu (WSL):

```bash
cd /mnt/d/2026-2/facturacion/infra/wsl
bash install-couchdb.sh      # solo la primera vez (pide la clave de sudo)
bash start-couchdb.sh        # cada vez que se reinicie WSL
```

Panel web: <http://localhost:5984/_utils> (usuario `admin`, clave `admin123`). WSL 2 expone el puerto 5984 a Windows automáticamente.

> **Plan B** si WSL da problemas el día de la presentación: `docker compose --profile backup-docker up -d` levanta el mismo CouchDB en Docker con las mismas credenciales; el backend no cambia.

**¿Por qué CouchDB como motor para ML?** Es un proyecto de la Apache Software Foundation que guarda documentos JSON (el formato que consumen directamente pandas/scikit-learn), tiene vistas **MapReduce** para agregaciones (el backend crea `unidades_por_producto`, `ventas_por_cliente`, `ventas_por_dia`) y un feed `_changes` para alimentar pipelines de entrenamiento en streaming.

Documento que se replica por cada factura:

```json
{
  "_id": "factura-1",
  "type": "invoice_backup",
  "invoice_id": 1,
  "client_id": "1032456789",
  "product_ids": ["6730e1...", "6730e2..."],
  "quantities": [2, 1],
  "totals": { "line_subtotals": [119800, 3199000], "subtotal": 3318800, "tax": 630572, "invoice_total": 3949372 },
  "timestamp": "2026-11-05T19:30:12.123",
  "backed_up_at": "2026-11-06T00:30:12.456Z",
  "source": "mysql.facturas"
}
```

### Paso 3 — Backend

```bash
cd backend
mvn spring-boot:run
```

API en <http://localhost:8080/api>. Pruebas unitarias: `mvn test`.

> **Equipos con poca RAM:** limitar el backend con
> `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx384m -XX:+UseSerialGC"` y la VM de WSL/Docker
> con `C:\Users\<usuario>\.wslconfig` (`[wsl2]` → `memory=4GB`), luego `wsl --shutdown`.
> Con todo encendido, las cuatro bases usan unos 1,6 GB.

### Paso 4 — Frontend

```bash
cd frontend
npm install
npm run dev
```

Abrir <http://localhost:5173>.

---

## 4. API REST

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/products` | Catálogo (MongoDB). `?onlyAvailable=true` filtra con stock |
| GET | `/api/products/{id}` · `/api/products/{id}/availability?quantity=n` | Consulta y disponibilidad |
| POST · PUT · DELETE | `/api/products` · `/api/products/{id}` | CRUD de productos |
| GET | `/api/persons?role=CLIENTE` | Personas (Cassandra), filtro opcional por rol |
| GET · POST · PUT · DELETE | `/api/persons/{document}` | CRUD de personas |
| POST | `/api/invoices` | **Emitir factura** `{ "clientId": "...", "items": [{ "productId": "...", "quantity": 2 }] }` |
| GET | `/api/invoices` · `/api/invoices/{id}` · `?clientId=` | Facturas (MySQL) |
| GET | `/api/backups` · `/api/backups/analytics` | Backup y analítica (CouchDB) |

Errores: `404` no encontrado, `422` regla de negocio (p. ej. stock insuficiente), `400` datos inválidos, `503` base de datos no disponible.

---

## 5. Guion sugerido para la presentación

1. **Arquitectura**: mostrar el árbol de paquetes y que `domain/` y `application/` no tienen imports de Spring. Correr `mvn test`.
2. **Concurrencia**: arrancar el backend y señalar el intercalado de `proceso-async-1/2/3` en consola.
3. **Facturar**: en la pestaña *Facturar*, elegir cliente (Cassandra), añadir productos (MongoDB), cambiar cantidades y ver subtotal, IVA y total en vivo. Emitir.
4. **Comprobante**: mostrar la factura generada y en consola el backup en otro hilo.
5. **Evidencia en cada motor**:
   ```bash
   docker exec -it facturacion-mysql mysql -ufacturacion -pfacturacion123 facturacion -e "SELECT * FROM facturas; SELECT * FROM detalle_factura;"
   docker exec -it facturacion-mongodb mongosh -u root -p root123 --quiet --eval "db.getSiblingDB('facturacion').productos.find({}, {nombre:1, stock:1})"
   docker exec -it facturacion-cassandra cqlsh -e "SELECT documento, nombre, rol FROM facturacion.personas;"
   ```
   CouchDB: <http://localhost:5984/_utils> → base `facturas_backup`.
6. **Historial y backup**: la pestaña muestra cada factura con su estado de réplica y las vistas MapReduce.
7. **Tolerancia a fallos**: detener CouchDB, emitir otra factura (se emite igual y queda en `backup-pendiente/`), volver a iniciarlo.

## 6. Credenciales de desarrollo

| Motor | Host:puerto | Usuario / clave | Base |
|---|---|---|---|
| MySQL | `localhost:3307` | `facturacion` / `facturacion123` (root: `root123`) | `facturacion` |
| MongoDB | `localhost:27018` | `root` / `root123` | `facturacion` |
| Cassandra | `localhost:9042` | — | keyspace `facturacion` |
| CouchDB | `localhost:5984` | `admin` / `admin123` | `facturas_backup` |

Todas se pueden cambiar con variables de entorno (`MYSQL_PORT`, `MONGO_HOST`, `COUCHDB_PASSWORD`, …) definidas en [application.yml](backend/src/main/resources/application.yml).
