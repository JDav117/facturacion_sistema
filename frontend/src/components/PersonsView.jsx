import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import { dateTime } from '../format.js';

const EMPTY = {
  document: '', documentType: 'CC', personType: 'NATURAL', fullName: '',
  email: '', phone: '', address: '', city: '', role: 'CLIENTE'
};

const ROLE_LABEL = { CLIENTE: 'Cliente', EMPLEADO: 'Empleado', PROVEEDOR: 'Proveedor' };

export default function PersonsView() {
  const [persons, setPersons] = useState([]);
  const [roleFilter, setRoleFilter] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState(null);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setPersons(await api.getPersons(roleFilter || undefined));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [roleFilter]);

  useEffect(() => { load(); }, [load]);

  const set = (field) => (e) => {
    const value = e.target.value;
    setForm((f) => {
      const next = { ...f, [field]: value };
      // Regla de negocio: persona jurídica => NIT
      if (field === 'personType' && value === 'JURIDICA') next.documentType = 'NIT';
      return next;
    });
  };

  async function submit(e) {
    e.preventDefault();
    setError(null);
    setMessage(null);
    try {
      if (editing) {
        await api.updatePerson(editing, form);
        setMessage(`Persona ${form.fullName} actualizada`);
      } else {
        await api.createPerson(form);
        setMessage(`Persona ${form.fullName} registrada en Cassandra`);
      }
      setForm(EMPTY);
      setEditing(null);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  function edit(person) {
    setEditing(person.document);
    setForm({ ...EMPTY, ...Object.fromEntries(Object.entries(person).map(([k, v]) => [k, v ?? ''])) });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function remove(person) {
    if (!window.confirm(`¿Eliminar a ${person.fullName}?`)) return;
    try {
      await api.deletePerson(person.document);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="two-cols">
      <form className="card form" onSubmit={submit}>
        <div className="card-header">
          <h2>{editing ? 'Editar persona' : 'Registrar persona'}</h2>
          <span className="tag">Cassandra</span>
        </div>

        <label>Rol
          <select className="input" value={form.role} onChange={set('role')}>
            <option value="CLIENTE">Cliente</option>
            <option value="EMPLEADO">Empleado</option>
            <option value="PROVEEDOR">Proveedor</option>
          </select>
        </label>
        <div className="row">
          <label>Tipo de persona
            <select className="input" value={form.personType} onChange={set('personType')}>
              <option value="NATURAL">Natural</option>
              <option value="JURIDICA">Jurídica</option>
            </select>
          </label>
          <label>Tipo de documento
            <select className="input" value={form.documentType} onChange={set('documentType')}>
              {['CC', 'CE', 'TI', 'PASAPORTE', 'NIT'].map((t) => <option key={t}>{t}</option>)}
            </select>
          </label>
        </div>
        <label>Documento (clave)
          <input className="input" required value={form.document} onChange={set('document')} disabled={Boolean(editing)} />
        </label>
        <label>{form.personType === 'JURIDICA' ? 'Razón social' : 'Nombre completo'}
          <input className="input" required value={form.fullName} onChange={set('fullName')} />
        </label>
        <div className="row">
          <label>Correo
            <input className="input" type="email" value={form.email} onChange={set('email')} />
          </label>
          <label>Teléfono
            <input className="input" value={form.phone} onChange={set('phone')} />
          </label>
        </div>
        <div className="row">
          <label>Dirección
            <input className="input" value={form.address} onChange={set('address')} />
          </label>
          <label>Ciudad
            <input className="input" value={form.city} onChange={set('city')} />
          </label>
        </div>

        {error && <div className="alert error">{error}</div>}
        {message && <div className="alert ok">{message}</div>}

        <div className="row">
          <button className="btn primary" type="submit">{editing ? 'Guardar cambios' : 'Registrar'}</button>
          {editing && (
            <button className="btn ghost" type="button" onClick={() => { setEditing(null); setForm(EMPTY); }}>
              Cancelar
            </button>
          )}
        </div>
      </form>

      <div className="card">
        <div className="card-header">
          <h2>Personas registradas ({persons.length})</h2>
          <select className="input compact" value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)}>
            <option value="">Todos los roles</option>
            <option value="CLIENTE">Clientes</option>
            <option value="EMPLEADO">Empleados</option>
            <option value="PROVEEDOR">Proveedores</option>
          </select>
        </div>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr><th>Documento</th><th>Nombre</th><th>Rol</th><th>Contacto</th><th>Registro</th><th /></tr>
            </thead>
            <tbody>
              {persons.map((p) => (
                <tr key={p.document}>
                  <td className="nowrap">{p.documentType} {p.document}</td>
                  <td>
                    {p.fullName}
                    <div className="muted tiny">{p.personType === 'JURIDICA' ? 'Jurídica' : 'Natural'}</div>
                  </td>
                  <td><span className={`badge role-${p.role.toLowerCase()}`}>{ROLE_LABEL[p.role]}</span></td>
                  <td className="small">{p.email}<div className="muted tiny">{[p.phone, p.city].filter(Boolean).join(' · ')}</div></td>
                  <td className="small nowrap">{dateTime(p.registeredAt)}</td>
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
