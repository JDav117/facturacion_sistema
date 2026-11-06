import { useCallback, useEffect, useMemo, useReducer, useState } from 'react';
import { api } from '../api.js';
import ClientSelector from './ClientSelector.jsx';
import ProductCatalog from './ProductCatalog.jsx';
import InvoiceDetailTable from './InvoiceDetailTable.jsx';
import InvoiceReceipt from './InvoiceReceipt.jsx';

// ----------------------------------------------------------------------------
// Estado de la orden de compra: cliente seleccionado + líneas { product, quantity }.
// ----------------------------------------------------------------------------
const initialOrder = { client: null, items: [] };

function orderReducer(state, action) {
  switch (action.type) {
    case 'SELECT_CLIENT':
      return { ...state, client: action.client };

    case 'ADD_ITEM': {
      const { product, quantity } = action;
      const existing = state.items.find((i) => i.product.id === product.id);
      const newQuantity = Math.min((existing?.quantity ?? 0) + quantity, product.stock);
      if (existing) {
        return {
          ...state,
          items: state.items.map((i) => (i.product.id === product.id ? { ...i, quantity: newQuantity } : i))
        };
      }
      return { ...state, items: [...state.items, { product, quantity: newQuantity }] };
    }

    case 'SET_QUANTITY':
      return {
        ...state,
        items: state.items.map((i) =>
          i.product.id === action.productId
            ? { ...i, quantity: Math.max(1, Math.min(action.quantity || 1, i.product.stock)) }
            : i
        )
      };

    case 'REMOVE_ITEM':
      return { ...state, items: state.items.filter((i) => i.product.id !== action.productId) };

    case 'RESET':
      return initialOrder;

    default:
      return state;
  }
}

export default function BillingSystem() {
  const [order, dispatch] = useReducer(orderReducer, initialOrder);
  const [clients, setClients] = useState([]);
  const [products, setProducts] = useState([]);
  const [taxRate, setTaxRate] = useState(0.19);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [emitting, setEmitting] = useState(false);
  const [emitError, setEmitError] = useState(null);
  const [issued, setIssued] = useState(null); // { invoice, client }

  const loadData = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      // Cassandra y MongoDB se consultan en paralelo desde el navegador.
      const [clientList, productList, config] = await Promise.all([
        api.getPersons('CLIENTE'),
        api.getProducts(),
        api.getInvoiceConfig()
      ]);
      setClients(clientList);
      setProducts(productList);
      setTaxRate(Number(config.taxRate));
    } catch (e) {
      setLoadError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Cálculo en vivo de subtotales, IVA y total. Es una vista previa: el valor oficial lo calcula el backend.
  const lines = useMemo(
    () =>
      order.items.map(({ product, quantity }) => ({
        product,
        quantity,
        unitPrice: Number(product.salePrice),
        subtotal: Number(product.salePrice) * quantity
      })),
    [order.items]
  );
  const subtotal = useMemo(() => lines.reduce((sum, l) => sum + l.subtotal, 0), [lines]);
  const taxAmount = Math.round(subtotal * taxRate * 100) / 100;
  const total = subtotal + taxAmount;
  const unitCount = useMemo(() => lines.reduce((sum, l) => sum + l.quantity, 0), [lines]);

  const canEmit = Boolean(order.client) && lines.length > 0 && !emitting;

  async function emitInvoice() {
    if (!canEmit) return;
    setEmitting(true);
    setEmitError(null);
    try {
      const invoice = await api.createInvoice({
        clientId: order.client.document,
        items: order.items.map((i) => ({ productId: i.product.id, quantity: i.quantity }))
      });
      setIssued({ invoice, client: order.client });
      dispatch({ type: 'RESET' });
      api.getProducts().then(setProducts).catch(() => {}); // refresca stock
    } catch (e) {
      setEmitError(e.message);
    } finally {
      setEmitting(false);
    }
  }

  if (issued) {
    return <InvoiceReceipt invoice={issued.invoice} client={issued.client} onClose={() => setIssued(null)} />;
  }

  if (loading) return <div className="card muted">Cargando clientes y catálogo…</div>;

  if (loadError) {
    return (
      <div className="card">
        <div className="alert error">{loadError}</div>
        <button className="btn" onClick={loadData}>Reintentar</button>
      </div>
    );
  }

  return (
    <div className="billing-layout">
      <section className="billing-main">
        <ClientSelector
          clients={clients}
          selected={order.client}
          onSelect={(client) => dispatch({ type: 'SELECT_CLIENT', client })}
        />
        <ProductCatalog
          products={products}
          order={order.items}
          onAdd={(product, quantity) => dispatch({ type: 'ADD_ITEM', product, quantity })}
        />
      </section>

      <aside className="billing-side">
        <InvoiceDetailTable
          client={order.client}
          lines={lines}
          subtotal={subtotal}
          taxRate={taxRate}
          taxAmount={taxAmount}
          total={total}
          unitCount={unitCount}
          onQuantity={(productId, quantity) => dispatch({ type: 'SET_QUANTITY', productId, quantity })}
          onRemove={(productId) => dispatch({ type: 'REMOVE_ITEM', productId })}
          onClear={() => dispatch({ type: 'RESET' })}
          onEmit={emitInvoice}
          canEmit={canEmit}
          emitting={emitting}
          error={emitError}
        />
      </aside>
    </div>
  );
}
