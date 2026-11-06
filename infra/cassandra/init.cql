-- =====================================================================
-- Apache Cassandra: registro de personas (clientes, empleados, proveedores)
-- Modelo plano orientado a consultas: la clave de particion es el documento,
-- asi cada lectura por documento va directo a un solo nodo, sin JOINs.
-- =====================================================================
CREATE KEYSPACE IF NOT EXISTS facturacion
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE facturacion;

CREATE TABLE IF NOT EXISTS personas (
    documento       text PRIMARY KEY,
    tipo_documento  text,       -- CC | CE | TI | PASAPORTE | NIT
    tipo_persona    text,       -- NATURAL | JURIDICA
    nombre          text,       -- nombre completo o razon social
    correo          text,
    telefono        text,
    direccion       text,
    ciudad          text,
    rol             text,       -- CLIENTE | EMPLEADO | PROVEEDOR
    fecha_registro  timestamp
);

-- Permite filtrar por rol (ej. listar solo clientes en el formulario de facturacion)
CREATE INDEX IF NOT EXISTS personas_rol_idx ON personas (rol);

-- ---------------- Clientes ----------------
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('1032456789', 'CC', 'NATURAL', 'Laura Valentina Gomez Rios', 'laura.gomez@example.com', '3104567890', 'Cra 15 # 85-20', 'Bogota', 'CLIENTE', toTimestamp(now())) IF NOT EXISTS;
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('1045789123', 'CC', 'NATURAL', 'Andres Felipe Martinez Cardenas', 'andres.martinez@example.com', '3157894561', 'Calle 50 # 45-12', 'Medellin', 'CLIENTE', toTimestamp(now())) IF NOT EXISTS;
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('1143987654', 'CC', 'NATURAL', 'Camila Andrea Herrera Pacheco', 'camila.herrera@example.com', '3008765432', 'Cra 53 # 79-30', 'Barranquilla', 'CLIENTE', toTimestamp(now())) IF NOT EXISTS;
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('1065432198', 'CC', 'NATURAL', 'Santiago Jose Ortega Mendoza', 'santiago.ortega@example.com', '3012345678', 'Calle 16 # 12-45', 'Valledupar', 'CLIENTE', toTimestamp(now())) IF NOT EXISTS;
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('E-845612', 'CE', 'NATURAL', 'Maria Fernanda Lopez Ruiz', 'maria.lopez@example.com', '3209876543', 'Av 6N # 23-50', 'Cali', 'CLIENTE', toTimestamp(now())) IF NOT EXISTS;
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('900456789-2', 'NIT', 'JURIDICA', 'Soluciones Educativas del Norte S.A.S.', 'compras@solnorte.example.com', '6055678901', 'Calle 72 # 54-35', 'Barranquilla', 'CLIENTE', toTimestamp(now())) IF NOT EXISTS;

-- ---------------- Empleados ----------------
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('1020304050', 'CC', 'NATURAL', 'Diana Carolina Suarez Pena', 'diana.suarez@example.com', '3112223344', 'Calle 100 # 19-61', 'Bogota', 'EMPLEADO', toTimestamp(now())) IF NOT EXISTS;
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('1098765432', 'CC', 'NATURAL', 'Julian David Castro Vargas', 'julian.castro@example.com', '3165554433', 'Cra 27 # 36-14', 'Bucaramanga', 'EMPLEADO', toTimestamp(now())) IF NOT EXISTS;

-- ---------------- Proveedores (personas juridicas, coinciden con los embebidos en MongoDB) ----------------
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('900123456-1', 'NIT', 'JURIDICA', 'Distribuidora TecnoAndina S.A.S.', 'ventas@tecnoandina.example.com', '6014567890', 'Av Calle 26 # 68-90', 'Bogota', 'PROVEEDOR', toTimestamp(now())) IF NOT EXISTS;
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('901234567-8', 'NIT', 'JURIDICA', 'Importadora Digital del Caribe Ltda.', 'pedidos@digitalcaribe.example.com', '6053456789', 'Via 40 # 71-120', 'Barranquilla', 'PROVEEDOR', toTimestamp(now())) IF NOT EXISTS;
INSERT INTO personas (documento, tipo_documento, tipo_persona, nombre, correo, telefono, direccion, ciudad, rol, fecha_registro)
VALUES ('900765432-3', 'NIT', 'JURIDICA', 'Perifericos Colombia S.A.S.', 'comercial@perifericos.example.com', '6043219876', 'Cra 43A # 1-50', 'Medellin', 'PROVEEDOR', toTimestamp(now())) IF NOT EXISTS;
