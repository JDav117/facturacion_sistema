-- =====================================================================
-- MySQL: modulo transaccional de facturacion
-- cliente_id  -> documento de la persona en Apache Cassandra (referencia logica)
-- producto_id -> _id del producto en MongoDB            (referencia logica)
-- No hay llave foranea hacia esos motores porque viven en otra base de datos.
-- =====================================================================
CREATE DATABASE IF NOT EXISTS facturacion CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE facturacion;

CREATE TABLE IF NOT EXISTS facturas (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    cliente_id      VARCHAR(30)   NOT NULL COMMENT 'Documento de la persona en Cassandra',
    cliente_nombre  VARCHAR(150)  NOT NULL COMMENT 'Copia del nombre al momento de facturar',
    fecha           DATETIME(6)   NOT NULL,
    subtotal        DECIMAL(14,2) NOT NULL COMMENT 'Suma de los subtotales de linea',
    porcentaje_iva  DECIMAL(5,4)  NOT NULL COMMENT 'Tarifa aplicada, ej. 0.1900',
    iva             DECIMAL(14,2) NOT NULL COMMENT 'subtotal * porcentaje_iva',
    total           DECIMAL(14,2) NOT NULL COMMENT 'subtotal + iva',
    PRIMARY KEY (id),
    INDEX idx_facturas_cliente (cliente_id),
    INDEX idx_facturas_fecha (fecha)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS detalle_factura (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    factura_id       BIGINT        NOT NULL,
    producto_id      VARCHAR(40)   NOT NULL COMMENT '_id del producto en MongoDB',
    producto_nombre  VARCHAR(150)  NOT NULL COMMENT 'Copia del nombre al momento de facturar',
    cantidad         INT           NOT NULL,
    valor_unitario   DECIMAL(14,2) NOT NULL,
    subtotal         DECIMAL(14,2) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_detalle_producto (producto_id),
    CONSTRAINT fk_detalle_factura FOREIGN KEY (factura_id) REFERENCES facturas (id) ON DELETE CASCADE,
    CONSTRAINT chk_cantidad CHECK (cantidad > 0)
) ENGINE = InnoDB;
