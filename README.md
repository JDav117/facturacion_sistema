# Sistema de Facturación — Arquitectura Hexagonal

Prototipo de facturación en **Spring Boot 3.4 (Java 21)** + **React 18**, con persistencia distribuida en cuatro motores:

| Motor | Rol en el sistema | Dónde corre |
|---|---|---|
| **MongoDB 7** | Catálogo de productos (costo, precio, imágenes, proveedor embebido) | Docker |
| **Apache Cassandra 4.1** | Personas: clientes, empleados y proveedores (naturales o jurídicas) | Docker |
| **MySQL 8.4** | Parte transaccional: `facturas` + `detalle_factura` (con subtotal, IVA 19 % y total) | Docker |
| **Apache Druid 38** | Backup analítico / ML: tabla de hechos `hechos_ventas` | **WSL2 (Ubuntu)** |

---

## 1. Arquitectura

```
                 ┌──────────────────────────── ADAPTADORES DE ENTRADA ────────────────────────────┐
  React (5173) ─►│ ProductController · PersonController · InvoiceController · BackupController      │
                 │ ConcurrencyDemoRunner (ApplicationRunner → 3 procesos @Async)                   │
                 └───────────────────────────────────────┬────────────────────────────────────────┘
                                                         │ puertos de entrada (casos de uso)
                 ┌───────────────────────────────────────▼────────────────────────────────────────┐
                 │ APLICACIÓN  CreateInvoiceUseCase · GenerateFactsUseCase (generar hechos) · …     │
                 │ DOMINIO     Product · Person · Invoice · InvoiceDetail · SalesFact               │
                 │             SalesFactFactory (transformación + validación de hechos)             │
                 └───────────────────────────────────────┬────────────────────────────────────────┘
                                                         │ puertos de salida (SPI)
                 ┌───────────────┬───────────────────────┼──────────────────┬──────────────────────┐
                 │ ProductMongo  │ PersonCassandra        │ InvoiceMySql     │ DruidIngestion       │
                 │ Adapter       │ Adapter                │ Adapter          │ Adapter              │
                 └───────┬───────┴──────────┬─────────────┴────────┬─────────┴──────────┬───────────┘
                      MongoDB           Cassandra               MySQL          Apache Druid (WSL2)
```

- El **dominio** y la **aplicación** no importan nada de Spring ni de ninguna base de datos. Los servicios se ensamblan en [BeanConfiguration.java](backend/src/main/java/com/facturacion/infrastructure/config/BeanConfiguration.java).
- Prueba de ello: [InvoiceServiceTest.java](backend/src/test/java/com/facturacion/application/service/InvoiceServiceTest.java) y [SalesFactFactoryTest.java](backend/src/test/java/com/facturacion/domain/service/SalesFactFactoryTest.java) ejecutan los casos de uso con puertos falsos en memoria, sin levantar ninguna base.

### Estructura de paquetes

```
backend/src/main/java/com/facturacion/
├── domain/
│   ├── model/        Product, Supplier, Person, Invoice, InvoiceDetail, SalesFact, InvoiceBackup, BackupAnalytics, enums
│   ├── service/      SalesFactFactory (genera y valida los hechos de venta)
│   └── exception/    DomainException, ResourceNotFoundException
├── application/
│   ├── port/in/      CreateInvoiceUseCase, GetInvoicesUseCase, GetProductsUseCase, ManageProductsUseCase,
│   │                 RegisterPersonUseCase, GetPersonsUseCase, GetBackupsUseCase, GenerateFactsUseCase
│   ├── port/out/     ProductRepositoryPort, PersonRepositoryPort, InvoiceRepositoryPort, InvoiceBackupRepositoryPort
│   └── service/      InvoiceService, ProductService, PersonService, BackupService, FactGenerationService
└── infrastructure/
    ├── adapter/in/rest/                    Controladores REST + DTOs + GlobalExceptionHandler
    ├── adapter/out/persistence/mongo/      ProductDocument, ProductMongoAdapter
    ├── adapter/out/persistence/cassandra/  PersonEntity, PersonCassandraAdapter
    ├── adapter/out/persistence/mysql/      InvoiceEntity, InvoiceDetailEntity, InvoiceMySqlAdapter
    ├── adapter/out/backup/druid/           DruidIngestionAdapter (adaptador de ingesta Druid)
    ├── concurrency/                        ProcesoInventario, ProcesoReportes, ProcesoNotificaciones, ConcurrencyDemoRunner
    └── config/                             AsyncConfig, BeanConfiguration, PersistenceConfig, WebConfig
```

### Flujo de "Emitir factura" ([InvoiceService.java](backend/src/main/java/com/facturacion/application/service/InvoiceService.java))

1. Consulta **en paralelo** el cliente en Cassandra y los productos en MongoDB (dos métodos `@Async` que devuelven `CompletableFuture`).
2. Valida reglas: el cliente existe y tiene rol CLIENTE, cada producto existe, está activo y tiene stock. **El precio se toma del catálogo**, nunca del navegador. Calcula subtotal, **IVA (19 %, configurable en `facturacion.iva`)** y total.
3. Guarda `facturas` + `detalle_factura` en MySQL en **una sola transacción**.
4. Descuenta stock en MongoDB con una actualización atómica (`stock >= cantidad`).
5. **Genera los hechos** de la factura y los carga en Druid en un hilo `proceso-async-N`. La respuesta HTTP no lo espera; si Druid está caído, los hechos se guardan en `backend/backup-pendiente/hechos.jsonl` y la factura no falla.

### Caso de uso "generar hechos" ([FactGenerationService.java](backend/src/main/java/com/facturacion/application/service/FactGenerationService.java))

ETL completo que se lanza desde la pestaña *Historial y backup* (`POST /api/backups/regenerate`):

| Etapa | Qué hace |
|---|---|
| **Extracción** | Facturas de MySQL, personas de Cassandra y productos de MongoDB (cada uno por su puerto) |
| **Transformación** | [SalesFactFactory](backend/src/main/java/com/facturacion/domain/service/SalesFactFactory.java): una fila plana por línea de factura, con datos del cliente, producto y proveedor; reparte el IVA por línea y calcula el margen (`subtotal − costo`) |
| **Validación** | Cantidades positivas y que la suma de los hechos sea **exactamente** el total de la factura (la última línea absorbe el redondeo del IVA) |
| **Carga** | `DruidIngestionAdapter` envía una tarea de ingesta nativa a Druid que reemplaza la tabla de hechos |

Fila de la tabla de hechos `hechos_ventas` en Druid:

```json
{
  "__time": "2026-11-06T01:28:54.232Z",
  "invoice_id": 4, "invoice_number": "FAC-000004",
  "client_id": "1045789123", "client_name": "Andres Felipe Martinez Cardenas", "client_type": "NATURAL", "client_city": "Medellin",
  "product_id": "6aed2719512acbee5eac5498", "product_code": "MOU-185", "product_name": "Mouse inalambrico optico",
  "supplier_id": "900765432-3", "supplier_name": "Perifericos Colombia S.A.S.",
  "quantity": 2, "unit_price": 59900, "unit_cost": 38000,
  "line_subtotal": 119800, "line_tax": 22762, "line_total": 142562, "line_margin": 43800,
  "loaded_at": "2026-11-06T01:29:10.120Z"
}
```

**¿Por qué Apache Druid?** Es una base de datos columnar de la Apache Software Foundation pensada para analítica en tiempo real sobre tablas de hechos: agrega millones de filas en milisegundos con SQL (`/druid/v2/sql`) y sirve como fuente de *features* para modelos de Machine Learning (ventas por producto, cliente, día, margen por proveedor).

---

## 2. Concurrencia (tema de clase: hilos → `@Async`)

| Elemento | Dónde |
|---|---|
| `@EnableAsync` | [FacturacionApplication.java](backend/src/main/java/com/facturacion/FacturacionApplication.java) |
| Bean `ThreadPoolTaskExecutor` **`procesosExecutor`** (core 3, max 10, cola 0, prefijo `proceso-async-`) | [AsyncConfig.java](backend/src/main/java/com/facturacion/infrastructure/config/AsyncConfig.java) |
| `@Async("procesosExecutor")` en adaptadores de persistencia | `ProductMongoAdapter.findAllByIdsAsync`, `PersonCassandraAdapter.findByDocumentAsync`, `DruidIngestionAdapter.backup` |
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
[proceso-async-3 ] ProductMongoAdapter      : Consultando 2 producto(s) en MongoDB
[proceso-async-2 ] PersonCassandraAdapter   : Consultando cliente 1032456789 en Cassandra
[http-nio-8080-exec-3] InvoiceService       : Factura FAC-000005 registrada en MySQL ...
[proceso-async-1 ] DruidIngestionAdapter    : 2 hecho(s) de FAC-000005 enviados a Druid (tarea index_parallel_...)
[proceso-async-1 ] DruidIngestionAdapter    : Hechos de FAC-000005 cargados en Druid 'hechos_ventas' en 22786 ms
```

> Con `queueCapacity = 0`, si los 10 hilos están ocupados se usa `CallerRunsPolicy`: la tarea la ejecuta el hilo que la pidió en vez de rechazarse, así no se pierde ningún backup.

---

## 3. Puesta en marcha

### Requisitos
Java 21, Maven 3.9+, Node 18+, Docker Desktop y WSL 2 con Ubuntu.
Memoria (probado en un equipo de 16 GB): Docker y Ubuntu comparten una sola VM de WSL. Limitarla en `C:\Users\<usuario>\.wslconfig`:

```ini
[wsl2]
memory=5GB
swap=4GB

[experimental]
autoMemoryReclaim=dropcache
```

y luego `wsl --shutdown`. Sin límite, la VM crece con la caché de Linux hasta dejar a Windows sin memoria virtual y se cierran programas (VS Code incluido).
Consumo medido con todo encendido: bases en Docker ~1,6 GB + Druid ~1,1 GB + backend ~0,5 GB.

### Paso 1 — Bases de datos en Docker

```bash
docker compose up -d
docker compose logs -f cassandra-init   # esperar a que termine (Cassandra tarda ~1 min en arrancar)
```

Esto crea y **puebla** automáticamente:
- MySQL → [infra/mysql/01-schema.sql](infra/mysql/01-schema.sql) (puerto **3307** en el host para no chocar con un MySQL local)
- MongoDB → [infra/mongo/01-productos.js](infra/mongo/01-productos.js) (9 productos con imágenes, costo, precio y proveedor; puerto **27018** en el host para no chocar con un MongoDB local)
- Cassandra → [infra/cassandra/init.cql](infra/cassandra/init.cql) (6 clientes, 2 empleados, 3 proveedores)

### Paso 2 — Apache Druid en WSL2

Desde Ubuntu (WSL), sin `sudo`:

```bash
cd /mnt/d/2026-2/facturacion/infra/wsl
bash install-druid.sh   # solo la primera vez: descarga Druid 38 (~650 MB) y un JDK 21 portátil en ~/jdk-druid
bash start-druid.sh     # arranca Druid (~30-60 s) y DEJAR ESTA TERMINAL ABIERTA
```

- **La terminal de Ubuntu debe quedar abierta** mientras se usa el sistema: WSL apaga Ubuntu (y con él a Druid) cuando no queda ninguna terminal abierta. `Ctrl+C` detiene Druid (o `bash stop-druid.sh` desde otra terminal).
- `start-druid.sh` usa el perfil **micro-facturacion**: una versión reducida del perfil oficial *nano-quickstart* (heaps a la mitad, GC serial, un solo slot de ingesta). Druid completo queda en ~1,1 GB de RAM.
- Druid 38 **exige Java 21**. Por eso el instalador descarga uno propio y no usa el Java del sistema.
- Las JVM de Druid arrancan con `-Djava.net.preferIPv4Stack=true`. Sin eso, WSL solo publica el puerto en `[::1]` y el backend (que usa `127.0.0.1`) recibe *Connection refused*.

Consola web de Druid: <http://localhost:8888>.
La tabla `hechos_ventas` se crea sola con la primera factura o con el botón **Generar hechos (ETL)**. Cada carga tarda ~15-25 s en Druid (corre en segundo plano y no frena la facturación).

### Paso 3 — Backend

```bash
cd backend
mvn spring-boot:run
```

API en <http://localhost:8080/api>. Pruebas unitarias: `mvn test`.

> **Equipos con poca RAM:** limitar el backend con
> `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx384m -XX:+UseSerialGC"`.

### Paso 4 — Frontend

```bash
cd frontend
npm install
npm run dev
```

Abrir <http://localhost:5173>.

---

## 4. Imágenes de los productos

- **Dónde se guardan:** cada producto en MongoDB tiene el campo `imagenes` (lista de URLs). Es la forma habitual: la base guarda la referencia y el archivo lo sirve un servidor web o un CDN.
- **Archivos de ejemplo:** las 9 imágenes del catálogo inicial están en [frontend/public/img/productos/](frontend/public/img/productos/) y se sirven como `/img/productos/<nombre>.svg`.
- **Dónde se ven:** en la pestaña *Facturar* (tarjetas del catálogo) y en *Productos* (miniaturas de la tabla).
- **Agregar o cambiar una imagen:** en la pestaña *Productos* → campo *URLs de imágenes* (separadas por coma). Sirve una URL de internet (`https://…`) o un archivo copiado a `frontend/public/img/productos/`.

---

## 5. API REST

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/products` | Catálogo (MongoDB). `?onlyAvailable=true` filtra con stock |
| GET | `/api/products/{id}` · `/api/products/{id}/availability?quantity=n` | Consulta y disponibilidad |
| POST · PUT · DELETE | `/api/products` · `/api/products/{id}` | CRUD de productos |
| GET | `/api/persons?role=CLIENTE` | Personas (Cassandra), filtro opcional por rol |
| GET · POST · PUT · DELETE | `/api/persons/{document}` | CRUD de personas |
| POST | `/api/invoices` | **Emitir factura** `{ "clientId": "...", "items": [{ "productId": "...", "quantity": 2 }] }` |
| GET | `/api/invoices` · `/api/invoices/{id}` · `?clientId=` | Facturas (MySQL) |
| GET | `/api/invoices/config` | Tarifa de IVA vigente |
| GET | `/api/backups` · `/api/backups/analytics` | Hechos por factura y analítica (Druid SQL) |
| POST | `/api/backups/regenerate` | **Generar hechos**: ETL completo MySQL + Cassandra + MongoDB → Druid |

Errores: `404` no encontrado, `422` regla de negocio (p. ej. stock insuficiente), `400` datos inválidos, `503` base de datos no disponible.

---

## 6. Guion sugerido para la presentación

1. **Arquitectura**: mostrar el árbol de paquetes y que `domain/` y `application/` no tienen imports de Spring. Correr `mvn test`.
2. **Concurrencia**: arrancar el backend y señalar el intercalado de `proceso-async-1/2/3` en consola.
3. **Facturar**: en la pestaña *Facturar*, elegir cliente (Cassandra), añadir productos (MongoDB), cambiar cantidades y ver subtotal, IVA y total en vivo. Emitir.
4. **Comprobante**: mostrar la factura generada y en consola la carga de hechos a Druid en otro hilo.
5. **Evidencia en cada motor**:
   ```bash
   docker exec -it facturacion-mysql mysql -ufacturacion -pfacturacion123 facturacion -e "SELECT * FROM facturas; SELECT * FROM detalle_factura;"
   docker exec -it facturacion-mongodb mongosh -u root -p root123 --quiet --eval "db.getSiblingDB('facturacion').productos.find({}, {nombre:1, stock:1})"
   docker exec -it facturacion-cassandra cqlsh -e "SELECT documento, nombre, rol FROM facturacion.personas;"
   ```
   Druid: <http://localhost:8888> → *Query* → `SELECT * FROM hechos_ventas`.
6. **Generar hechos**: en *Historial y backup*, pulsar **Generar hechos (ETL)** y mostrar la tarea en la consola de Druid (*Tasks*).
7. **Tolerancia a fallos**: detener Druid (`bash stop-druid.sh`), emitir otra factura (se emite igual y los hechos quedan en `backup-pendiente/`), volver a iniciarlo y regenerar.

## 7. Credenciales de desarrollo

| Motor | Host:puerto | Usuario / clave | Base |
|---|---|---|---|
| MySQL | `localhost:3307` | `facturacion` / `facturacion123` (root: `root123`) | `facturacion` |
| MongoDB | `localhost:27018` | `root` / `root123` | `facturacion` |
| Cassandra | `localhost:9042` | — | keyspace `facturacion` |
| Apache Druid | `localhost:8888` | — (sin autenticación en modo local) | datasource `hechos_ventas` |

Todas se pueden cambiar con variables de entorno (`MYSQL_PORT`, `MONGO_HOST`, `DRUID_HOST`, …) definidas en [application.yml](backend/src/main/resources/application.yml).
