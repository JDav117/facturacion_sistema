import { useState } from 'react';
import BillingSystem from './components/BillingSystem.jsx';
import PersonsView from './components/PersonsView.jsx';
import ProductsView from './components/ProductsView.jsx';
import HistoryView from './components/HistoryView.jsx';

const TABS = [
  { id: 'billing', label: 'Facturar', engine: 'MySQL' },
  { id: 'persons', label: 'Personas', engine: 'Cassandra' },
  { id: 'products', label: 'Productos', engine: 'MongoDB' },
  { id: 'history', label: 'Historial y backup', engine: 'MySQL + CouchDB' }
];

export default function App() {
  const [tab, setTab] = useState('billing');

  return (
    <div className="app">
      <header className="topbar no-print">
        <div className="logo">
          <span className="logo-mark">⬡</span>
          <div>
            <div className="logo-title">Facturación Hexagonal</div>
            <div className="logo-sub">Spring Boot · MySQL · MongoDB · Cassandra · CouchDB</div>
          </div>
        </div>
        <nav className="tabs">
          {TABS.map((t) => (
            <button
              key={t.id}
              className={`tab ${tab === t.id ? 'active' : ''}`}
              onClick={() => setTab(t.id)}
              title={t.engine}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </header>

      <main className="content">
        {tab === 'billing' && <BillingSystem />}
        {tab === 'persons' && <PersonsView />}
        {tab === 'products' && <ProductsView />}
        {tab === 'history' && <HistoryView />}
      </main>
    </div>
  );
}
