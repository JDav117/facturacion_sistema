import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import { money, PLACEHOLDER_IMG } from '../format.js';

const EMPTY = {
  code: '', name: '', description: '', cost: '', salePrice: '', stock: 0, active: true,
  supplierDocument: '', imageUrls: ''
};

export default function ProductsView() {
  const [products, setProducts] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState(null);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      const [productList, supplierList] = await Promise.all([api.getProducts(), api.getPersons('PROVEEDOR')]);
      setProducts(productList);
      setSuppliers(supplierList);
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const set = (field) => (e) =>
    setForm((f) => ({ ...f, [field]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

  function toPayload() {
    const s = suppliers.find((x) => x.document === form.supplierDocument);
    return {
      code: form.code,
      name: form.name,
      description: form.description,
      cost: Number(form.cost),
      salePrice: Number(form.salePrice),
      stock: Number(form.stock),
      active: form.active,
      // El proveedor (persona jurídica de Cassandra) se embebe dentro del documento de MongoDB
      supplier: s ? { document: s.document, name: s.fullName, email: s.email, phone: s.phone } : null,
      imageUrls: form.imageUrls.split(',').map((u) => u.trim()).filter(Boolean)
    };
  }

  async function submit(e) {
    e.preventDefault();
    setError(null);
    setMessage(null);
    try {
      if (editing) {
        await api.updateProduct(editing, toPayload());
        setMessage(`Producto ${form.name} actualizado`);
      } else {
        await api.createProduct(toPayload());
        setMessage(`Producto ${form.name} creado en MongoDB`);
      }
      setForm(EMPTY);
      setEditing(null);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  function edit(p) {
    setEditing(p.id);
    setForm({
      code: p.code, name: p.name, description: p.description ?? '', cost: p.cost, salePrice: p.salePrice,
      stock: p.stock, active: p.active, supplierDocument: p.supplier?.document ?? '',
      imageUrls: (p.imageUrls ?? []).join(', ')
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function remove(p) {
    if (!window.confirm(`¿Eliminar ${p.name}?`)) return;
    try {
      await api.deleteProduct(p.id);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="two-cols">
      <form className="card form" onSubmit={submit}>
        <div className="card-header">
          <h2>{editing ? 'Editar producto' : 'Nuevo producto'}</h2>
          <span className="tag">MongoDB</span>
        </div>
        <div className="row">
          <label>Código
            <input className="input" required value={form.code} onChange={set('code')} />
          </label>
          <label>Stock
            <input className="input" type="number" min={0} required value={form.stock} onChange={set('stock')} />
          </label>
        </div>
        <label>Nombre
          <input className="input" required value={form.name} onChange={set('name')} />
        </label>
        <label>Descripción
          <textarea className="input" rows={2} value={form.description} onChange={set('description')} />
        </label>
        <div className="row">
          <label>Costo
            <input className="input" type="number" min={0} step="any" required value={form.cost} onChange={set('cost')} />
          </label>
          <label>Precio de venta
            <input className="input" type="number" min={1} step="any" required value={form.salePrice} onChange={set('salePrice')} />
          </label>
        </div>
        <label>Proveedor
          <select className="input" value={form.supplierDocument} onChange={set('supplierDocument')}>
            <option value="">— Sin proveedor —</option>
            {suppliers.map((s) => <option key={s.document} value={s.document}>{s.fullName} ({s.document})</option>)}
          </select>
        </label>
        <label>URLs de imágenes (separadas por coma)
          <input className="input" value={form.imageUrls} onChange={set('imageUrls')} placeholder="/img/productos/laptop.svg, https://…" />
        </label>
        <label className="checkbox">
          <input type="checkbox" checked={form.active} onChange={set('active')} /> Activo
        </label>

        {error && <div className="alert error">{error}</div>}
        {message && <div className="alert ok">{message}</div>}

        <div className="row">
          <button className="btn primary" type="submit">{editing ? 'Guardar cambios' : 'Crear producto'}</button>
          {editing && (
            <button className="btn ghost" type="button" onClick={() => { setEditing(null); setForm(EMPTY); }}>
              Cancelar
            </button>
          )}
        </div>
      </form>

      <div className="card">
        <div className="card-header">
          <h2>Catálogo ({products.length})</h2>
        </div>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr><th /><th>Producto</th><th className="num">Costo</th><th className="num">Precio</th><th className="num">Margen</th><th className="num">Stock</th><th /></tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id} className={p.active ? '' : 'inactive'}>
                  <td>
                    <img className="thumb" src={p.imageUrls?.[0] || PLACEHOLDER_IMG} alt=""
                      onError={(e) => { e.currentTarget.src = PLACEHOLDER_IMG; }} />
                  </td>
                  <td>
                    {p.name}
                    <div className="muted tiny">{p.code} · {p.supplier?.name ?? 'sin proveedor'}</div>
                    <div className="muted tiny">_id: {p.id}</div>
                  </td>
                  <td className="num">{money(p.cost)}</td>
                  <td className="num">{money(p.salePrice)}</td>
                  <td className="num">{money(p.margin)}</td>
                  <td className={`num ${p.stock < 5 ? 'low' : ''}`}>{p.stock}</td>
                  <td className="nowrap">
                    <button className="icon-btn" title="Editar" onClick={() => edit(p)}>✎</button>
                    <button className="icon-btn" title="Eliminar" onClick={() => remove(p)}>✕</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
