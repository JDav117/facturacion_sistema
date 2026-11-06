import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import { dateTime, money } from '../format.js';
import InvoiceReceipt from './InvoiceReceipt.jsx';

function MetricList({ title, metrics, label, format }) {
  const max = Math.max(...metrics.map((m) => Number(m.value)), 1);
  return (
    <div className="metric-block">
      <h3>{title}</h3>
      {metrics.length === 0 && <p className="muted small">Sin datos todavía.</p>}
      {metrics.slice(0, 6).map((m) => (
        <div key={m.key} className="metric-row">
          <span className="metric-label" title={m.key}>{label(m.key)}</span>
          <span className="metric-bar"><span style={{ width: `${(Number(m.value) / max) * 100}%` }} /></span>
          <span className="metric-value">{format(m.value)}</span>
        </div>
      ))}
    </div>
  );
}

export default function HistoryView() {
  const [invoices, setInvoices] = useState([]);
  const [backups, setBackups] = useState([]);
  const [analytics, setAnalytics] = useState(null);
  const [error, setError] = useState(null);
  const [backupError, setBackupError] = useState(null);
  const [selected, setSelected] = useState(null);

  const load = useCallback(async () => {
    try {
      setInvoices(await api.getInvoices());
      setError(null);
    } catch (e) {
      setError(e.message);
    }
    try {
      const [b, a] = await Promise.all([api.getBackups(), api.getBackupAnalytics()]);
      setBackups(b);
      setAnalytics(a);
      setBackupError(null);
    } catch (e) {
      setBackupError(e.message);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const backedUpIds = useMemo(() => new Set(backups.map((b) => b.invoiceId)), [backups]);

  // Los backups solo guardan IDs: se resuelven nombres con los datos transaccionales.
  const productNames = useMemo(() => {
    const map = {};
    invoices.forEach((inv) => inv.details.forEach((d) => { map[d.productId] = d.productName; }));
    return map;
  }, [invoices]);
  const clientNames = useMemo(
    () => Object.fromEntries(invoices.map((inv) => [inv.clientId, inv.clientName])),
    [invoices]
  );

  if (selected) {
    return <InvoiceReceipt invoice={selected} onClose={() => setSelected(null)} closeLabel="← Volver al historial" />;
  }

  const totalSales = invoices.reduce((s, i) => s + Number(i.total), 0);

  return (
    <div className="history">
      <div className="stats">
        <div className="stat"><span className="muted small">Facturas (MySQL)</span><strong>{invoices.length}</strong></div>
        <div className="stat"><span className="muted small">Respaldadas (CouchDB)</span><strong>{backupError ? '—' : backups.length}</strong></div>
        <div className="stat"><span className="muted small">Ventas totales</span><strong>{money(totalSales)}</strong></div>
        <button className="btn" onClick={load}>Actualizar</button>
      </div>

      {error && <div className="alert error">{error}</div>}

      <div className="card">
        <div className="card-header">
          <h2>Facturas emitidas</h2>
          <span className="tag">MySQL</span>
        </div>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr><th>Número</th><th>Fecha</th><th>Cliente</th><th className="num">Unidades</th><th className="num">Total</th><th>Backup</th><th /></tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id}>
                  <td className="nowrap"><strong>{inv.number}</strong></td>
                  <td className="nowrap small">{dateTime(inv.issuedAt)}</td>
                  <td>{inv.clientName}<div className="muted tiny">{inv.clientId}</div></td>
                  <td className="num">{inv.itemsCount}</td>
                  <td className="num">{money(inv.total)}</td>
                  <td>
                    {backupError
                      ? <span className="badge muted-badge">sin conexión</span>
                      : backedUpIds.has(inv.id)
                        ? <span className="badge ok-badge">✓ replicada</span>
                        : <span className="badge warn-badge">pendiente</span>}
                  </td>
                  <td><button className="btn ghost small" onClick={() => setSelected(inv)}>Ver</button></td>
                </tr>
              ))}
              {invoices.length === 0 && !error && (
                <tr><td colSpan={7} className="muted center">Aún no hay facturas.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2>Analítica del backup (vistas MapReduce)</h2>
          <span className="tag">Apache CouchDB · WSL</span>
        </div>
        {backupError ? (
          <div className="alert warn">{backupError}. Inicie CouchDB en WSL: <code>bash infra/wsl/start-couchdb.sh</code></div>
        ) : analytics && (
          <div className="metrics">
            <MetricList title="Unidades vendidas por producto" metrics={analytics.unitsByProduct}
              label={(k) => productNames[k] ?? k} format={(v) => Number(v)} />
            <MetricList title="Ventas por cliente" metrics={analytics.salesByClient}
              label={(k) => clientNames[k] ?? k} format={money} />
            <MetricList title="Ventas por día" metrics={analytics.salesByDay}
              label={(k) => k} format={money} />
          </div>
        )}
      </div>
    </div>
  );
}
