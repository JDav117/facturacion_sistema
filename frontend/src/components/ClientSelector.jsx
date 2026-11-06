import { useMemo, useState } from 'react';

export default function ClientSelector({ clients, selected, onSelect }) {
  const [query, setQuery] = useState('');

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return clients;
    return clients.filter(
      (c) => c.fullName.toLowerCase().includes(q) || c.document.toLowerCase().includes(q)
    );
  }, [clients, query]);

  return (
    <div className="card">
      <div className="card-header">
        <h2>1. Cliente</h2>
        <span className="tag">Cassandra · {clients.length} clientes</span>
      </div>

      {selected ? (
        <div className="client-selected">
          <div>
            <strong>{selected.fullName}</strong>
            <div className="muted small">
              {selected.documentType} {selected.document} · {selected.personType === 'JURIDICA' ? 'Persona jurídica' : 'Persona natural'}
            </div>
            <div className="muted small">
              {[selected.email, selected.phone, selected.city].filter(Boolean).join(' · ')}
            </div>
          </div>
          <button className="btn ghost" onClick={() => onSelect(null)}>Cambiar</button>
        </div>
      ) : (
        <>
          <input
            className="input"
            placeholder="Buscar por nombre o documento…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <ul className="client-list">
            {matches.map((c) => (
              <li key={c.document}>
                <button onClick={() => onSelect(c)}>
                  <span>{c.fullName}</span>
                  <span className="muted small">{c.documentType} {c.document} · {c.city || 'sin ciudad'}</span>
                </button>
              </li>
            ))}
            {matches.length === 0 && <li className="muted small empty">Sin coincidencias. Registre el cliente en la pestaña Personas.</li>}
          </ul>
        </>
      )}
    </div>
  );
}
