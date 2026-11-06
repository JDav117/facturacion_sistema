import { money } from '../format.js';

export default function InvoiceDetailTable({
  client, lines, subtotal, taxRate, taxAmount, total, unitCount, onQuantity, onRemove, onClear, onEmit, canEmit, emitting, error
}) {
  return (
    <div className="card detail-card">
      <div className="card-header">
        <h2>3. Detalle de la factura</h2>
        {lines.length > 0 && <button className="btn ghost small" onClick={onClear}>Vaciar</button>}
      </div>

      <div className="detail-client">
        {client ? <>Cliente: <strong>{client.fullName}</strong></> : <span className="muted">Seleccione un cliente</span>}
      </div>

      {lines.length === 0 ? (
        <p className="muted empty-detail">Añada productos desde el catálogo.</p>
      ) : (
        <table className="table detail-table">
          <thead>
            <tr>
              <th>Producto</th>
              <th className="num">Cant.</th>
              <th className="num">V. unitario</th>
              <th className="num">Subtotal</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {lines.map(({ product, quantity, unitPrice, subtotal }) => (
              <tr key={product.id}>
                <td>{product.name}</td>
                <td className="num">
                  <input
                    type="number"
                    className="input qty"
                    min={1}
                    max={product.stock}
                    value={quantity}
                    onChange={(e) => onQuantity(product.id, Number(e.target.value))}
                  />
                </td>
                <td className="num">{money(unitPrice)}</td>
                <td className="num">{money(subtotal)}</td>
                <td>
                  <button className="icon-btn" title="Quitar" onClick={() => onRemove(product.id)}>✕</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="totals">
        <div><span>Unidades</span><span>{unitCount}</span></div>
        <div><span>Subtotal</span><span>{money(subtotal)}</span></div>
        <div><span>IVA ({Math.round(taxRate * 100)}%)</span><span>{money(taxAmount)}</span></div>
        <div className="grand-total"><span>Total</span><span>{money(total)}</span></div>
      </div>

      {error && <div className="alert error">{error}</div>}

      <button className="btn primary block" disabled={!canEmit} onClick={onEmit}>
        {emitting ? 'Emitiendo…' : 'Emitir Factura'}
      </button>
      <p className="muted small center">
        Se registra en MySQL y se respalda de forma asíncrona en CouchDB (WSL).
      </p>
    </div>
  );
}
