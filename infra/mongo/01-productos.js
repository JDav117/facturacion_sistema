// =====================================================================
// MongoDB: catalogo de productos
// Cada producto lleva su proveedor EMBEBIDO (subdocumento) y una lista de URLs de imagenes.
// El _id (ObjectId) es el que MySQL guarda en detalle_factura.producto_id.
// =====================================================================
db = db.getSiblingDB('facturacion');

db.createCollection('productos', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['codigo', 'nombre', 'costo', 'precioVenta', 'stock'],
      properties: {
        codigo: { bsonType: 'string' },
        nombre: { bsonType: 'string' },
        descripcion: { bsonType: 'string' },
        costo: { bsonType: 'decimal', description: 'costo de compra' },
        precioVenta: { bsonType: 'decimal', description: 'precio de venta al publico' },
        stock: { bsonType: 'int', minimum: 0 },
        activo: { bsonType: 'bool' },
        proveedor: {
          bsonType: 'object',
          properties: {
            nit: { bsonType: 'string' },
            nombre: { bsonType: 'string' },
            correo: { bsonType: 'string' },
            telefono: { bsonType: 'string' }
          }
        },
        imagenes: { bsonType: 'array', items: { bsonType: 'string' } }
      }
    }
  }
});

db.productos.createIndex({ codigo: 1 }, { unique: true, name: 'codigo_1' });
db.productos.createIndex({ nombre: 'text', descripcion: 'text' });

const tecnoAndina = { nit: '900123456-1', nombre: 'Distribuidora TecnoAndina S.A.S.', correo: 'ventas@tecnoandina.example.com', telefono: '6014567890' };
const digitalCaribe = { nit: '901234567-8', nombre: 'Importadora Digital del Caribe Ltda.', correo: 'pedidos@digitalcaribe.example.com', telefono: '6053456789' };
const perifericos = { nit: '900765432-3', nombre: 'Perifericos Colombia S.A.S.', correo: 'comercial@perifericos.example.com', telefono: '6043219876' };

const producto = (codigo, nombre, descripcion, costo, precio, stock, proveedor, imagenes) => ({
  codigo, nombre, descripcion,
  costo: NumberDecimal(costo),
  precioVenta: NumberDecimal(precio),
  stock: NumberInt(stock),
  activo: true,
  proveedor,
  imagenes,
  fechaActualizacion: new Date()
});

db.productos.insertMany([
  producto('LAP-001', 'Portatil 14" Core i5 16GB', 'Portatil 14 pulgadas, procesador Intel Core i5 de 12a generacion, 16 GB RAM, SSD 512 GB.', '2400000', '3199000', 12, tecnoAndina,
    ['/img/productos/laptop.svg', '/img/productos/laptop-alt.svg']),
  producto('MON-024', 'Monitor 24" Full HD IPS', 'Monitor IPS de 24 pulgadas, 75 Hz, entradas HDMI y VGA.', '480000', '699000', 20, tecnoAndina,
    ['/img/productos/monitor.svg']),
  producto('MOU-185', 'Mouse inalambrico optico', 'Mouse inalambrico 2.4 GHz, 1000 DPI, receptor USB nano.', '38000', '59900', 80, perifericos,
    ['/img/productos/mouse.svg']),
  producto('TEC-552', 'Teclado mecanico RGB', 'Teclado mecanico switches rojos, iluminacion RGB, distribucion en espanol.', '120000', '189900', 35, perifericos,
    ['/img/productos/keyboard.svg']),
  producto('AUD-520', 'Audifonos Bluetooth over-ear', 'Audifonos inalambricos con 40 horas de bateria y microfono integrado.', '150000', '229900', 40, digitalCaribe,
    ['/img/productos/headphones.svg']),
  producto('SSD-1TB', 'Disco SSD NVMe 1TB', 'Unidad de estado solido M.2 NVMe, lectura hasta 3500 MB/s.', '210000', '309900', 50, digitalCaribe,
    ['/img/productos/ssd.svg']),
  producto('USB-064', 'Memoria USB 64GB 3.2', 'Memoria USB 3.2 de 64 GB con carcasa metalica.', '22000', '39900', 150, digitalCaribe,
    ['/img/productos/usb.svg']),
  producto('IMP-325', 'Impresora multifuncional tinta continua', 'Imprime, copia y escanea. Sistema de tinta continua de fabrica, WiFi.', '650000', '899000', 8, tecnoAndina,
    ['/img/productos/printer.svg']),
  producto('CAM-920', 'Camara web Full HD 1080p', 'Camara web 1080p con microfono estereo y enfoque automatico.', '210000', '329900', 3, perifericos,
    ['/img/productos/webcam.svg'])
]);

print('Productos cargados: ' + db.productos.countDocuments());
