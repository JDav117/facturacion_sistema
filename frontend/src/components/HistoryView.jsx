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
  const [etl, setEtl] = useState(null); // { running, message, error }

  async function regenerate() {
    setEtl({ running: true });
    try {
      const r = await api.regenerateFacts();
      setEtl({
        message: `ETL enviado: ${r.invoices} facturas → ${r.facts} hechos (tarea ${r.loadId}). ` +
          'Druid tarda unos segundos en procesarla; luego pulse Actualizar.'
      });
    } catch (e) {
      setEtl({ error: e.message });
    }
  }

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

  if (selected) {
    return <InvoiceReceipt invoice={selected} onClose={() => setSelected(null)} closeLabel="← Volver al historial" />;
  }

  const totalSales = invoices.reduce((s, i) => s + Number(i.total), 0);

  return (
    <div className="history">
      <div className="stats">
        <div className="stat"><span className="muted small">Facturas (MySQL)</span><strong>{invoices.length}</strong></div>
        <div className="stat"><span className="muted small">En tabla de hechos (Druid)</span><strong>{backupError ? '—' : backups.length}</strong></div>
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
                        ? <span className="badge ok-badge">✓ en Druid</span>
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
          <h2>Analítica de la tabla de hechos (Druid SQL)</h2>
          <div className="row-actions">
            <span className="tag">Apache Druid · WSL2</span>
            <button className="btn small" disabled={etl?.running || Boolean(backupError)} onClick={regenerate}
              title="Caso de uso generar hechos: MySQL + Cassandra + MongoDB → transformación y validación → Druid">
              {etl?.running ? 'Generando…' : 'Generar hechos (ETL)'}
            </button>
          </div>
        </div>
        {etl?.message && <div className="alert ok">{etl.message}</div>}
        {etl?.error && <div className="alert error">{etl.error}</div>}
        {backupError ? (
          <div className="alert warn">{backupError}. Inicie Druid en WSL: <code>bash infra/wsl/start-druid.sh</code></div>
        ) : analytics && (
          <div className="metrics">
            <MetricList title="Unidades vendidas por producto" metrics={analytics.unitsByProduct}
              label={(k) => k} format={(v) => Number(v)} />
            <MetricList title="Ventas por cliente (con IVA)" metrics={analytics.salesByClient}
              label={(k) => k} format={money} />
            <MetricList title="Ventas por día" metrics={analytics.salesByDay}
              label={(k) => k} format={money} />
            <MetricList title="Margen bruto por proveedor" metrics={analytics.marginBySupplier}
              label={(k) => k} format={money} />
          </div>
        )}
      </div>
    </div>
  );
}
