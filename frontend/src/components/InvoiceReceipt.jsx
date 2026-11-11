import { dateTime, money } from '../format.js';

/** Comprobante final de la factura emitida (imprimible). */
export default function InvoiceReceipt({ invoice, client, onClose, closeLabel = 'Nueva factura' }) {
  return (
    <div className="receipt-wrapper">
      <div className="receipt-actions no-print">
        <button className="btn" onClick={onClose}>{closeLabel}</button>
        <button className="btn primary" onClick={() => window.print()}>Imprimir</button>
      </div>

      <div className="receipt">
        <header className="receipt-header">
          <div>
            <div className="brand">Facturación Hexagonal</div>
            <div className="muted small">Prototipo académico · Arquitectura de Software</div>
          </div>
          <div className="receipt-number">
            <div className="muted small">Factura de venta</div>
            <div className="number">{invoice.number}</div>
            <div className="muted small">{dateTime(invoice.issuedAt)}</div>
          </div>
        </header>

        <section className="receipt-client">
          <div className="muted small">Cliente</div>
          <strong>{invoice.clientName}</strong>
          <div className="small">Documento: {client ? `${client.documentType} ` : ''}{invoice.clientId}</div>
          {client && (
            <div className="small muted">
              {[client.email, client.phone, client.address, client.city].filter(Boolean).join(' · ')}
            </div>
          )}
        </section>

        <table className="table receipt-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Producto</th>
              <th className="num">Cantidad</th>
              <th className="num">Valor unitario</th>
              <th className="num">Subtotal</th>
            </tr>
          </thead>
          <tbody>
            {invoice.details.map((d, i) => (
              <tr key={d.productId}>
                <td>{i + 1}</td>
                <td>
                  {d.productName}
                  <div className="muted tiny">ID MongoDB: {d.productId}</div>
                </td>
                <td className="num">{d.quantity}</td>
                <td className="num">{money(d.unitPrice)}</td>
                <td className="num">{money(d.subtotal)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="sub-row">
              <td colSpan={2}>{invoice.itemsCount} unidad(es)</td>
              <td colSpan={2} className="num">Subtotal</td>
              <td className="num">{money(invoice.subtotal)}</td>
            </tr>
            <tr className="sub-row">
              <td colSpan={2} />
              <td colSpan={2} className="num">IVA ({Math.round(Number(invoice.taxRate) * 100)}%)</td>
              <td className="num">{money(invoice.taxAmount)}</td>
            </tr>
            <tr>
              <td colSpan={2} />
              <td colSpan={2} className="num"><strong>TOTAL</strong></td>
              <td className="num"><strong>{money(invoice.total)}</strong></td>
            </tr>
          </tfoot>
        </table>

        <footer className="receipt-footer muted small">
          Registro transaccional: MySQL (facturas #{invoice.id}) · Cliente: Cassandra · Productos: MongoDB ·
          Hechos analíticos: Apache Druid (WSL2)
        </footer>
      </div>
    </div>
  );
}
