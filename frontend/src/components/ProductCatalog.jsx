import { useMemo, useState } from 'react';
import { money, PLACEHOLDER_IMG } from '../format.js';

function ProductCard({ product, inOrder, onAdd }) {
  const [quantity, setQuantity] = useState(1);
  const remaining = product.stock - inOrder;
  const disabled = !product.active || remaining <= 0;

  return (
    <article className={`product-card ${disabled ? 'is-disabled' : ''}`}>
      <img
        src={product.imageUrls?.[0] || PLACEHOLDER_IMG}
        alt={product.name}
        onError={(e) => { e.currentTarget.src = PLACEHOLDER_IMG; }}
      />
      <div className="product-body">
        <div className="muted small">{product.code}</div>
        <h3>{product.name}</h3>
        <div className="muted small">{product.supplier?.name}</div>
        <div className="product-price">{money(product.salePrice)}</div>
        <div className={`stock ${product.stock < 5 ? 'low' : ''}`}>
          {product.stock === 0 ? 'Agotado' : `${product.stock} disponibles`}
          {inOrder > 0 && <span> · {inOrder} en factura</span>}
        </div>
        <div className="product-actions">
          <input
            type="number"
            className="input qty"
            min={1}
            max={Math.max(remaining, 1)}
            value={quantity}
            disabled={disabled}
            onChange={(e) => setQuantity(Math.max(1, Number(e.target.value) || 1))}
          />
          <button
            className="btn primary"
            disabled={disabled || quantity > remaining}
            onClick={() => { onAdd(product, quantity); setQuantity(1); }}
          >
            Añadir
          </button>
        </div>
      </div>
    </article>
  );
}

export default function ProductCatalog({ products, order, onAdd }) {
  const [query, setQuery] = useState('');

  const inOrder = useMemo(
    () => Object.fromEntries(order.map((i) => [i.product.id, i.quantity])),
    [order]
  );

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return products.filter(
      (p) => p.active && (!q || p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q))
    );
  }, [products, query]);

  return (
    <div className="card">
      <div className="card-header">
        <h2>2. Productos</h2>
        <span className="tag">MongoDB · {products.length} productos</span>
      </div>
      <input
        className="input"
        placeholder="Buscar producto por nombre o código…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <div className="product-grid">
        {visible.map((p) => (
          <ProductCard key={p.id} product={p} inOrder={inOrder[p.id] ?? 0} onAdd={onAdd} />
        ))}
        {visible.length === 0 && <p className="muted">No hay productos que coincidan.</p>}
      </div>
    </div>
  );
}
