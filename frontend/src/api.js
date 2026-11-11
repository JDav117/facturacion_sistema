// Cliente HTTP del backend. Cada recurso corresponde a un adaptador REST del hexagono.
const BASE = import.meta.env.VITE_API_URL ?? '/api';

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(BASE + path, {
      headers: { 'Content-Type': 'application/json' },
      ...options
    });
  } catch {
    throw new Error('No hay conexión con el backend (¿está corriendo en el puerto 8080?)');
  }
  if (!response.ok) {
    let message = `Error ${response.status}`;
    try {
      const body = await response.json();
      message = body.message || message;
      if (body.details?.length) message += ` — ${body.details.join(', ')}`;
    } catch {
      /* respuesta sin cuerpo JSON */
    }
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

const json = (method, body) => ({ method, body: JSON.stringify(body) });

export const api = {
  // Personas (Cassandra)
  getPersons: (role) => request(role ? `/persons?role=${role}` : '/persons'),
  createPerson: (person) => request('/persons', json('POST', person)),
  updatePerson: (document, person) => request(`/persons/${encodeURIComponent(document)}`, json('PUT', person)),
  deletePerson: (document) => request(`/persons/${encodeURIComponent(document)}`, { method: 'DELETE' }),

  // Productos (MongoDB)
  getProducts: () => request('/products'),
  createProduct: (product) => request('/products', json('POST', product)),
  updateProduct: (id, product) => request(`/products/${id}`, json('PUT', product)),
  deleteProduct: (id) => request(`/products/${id}`, { method: 'DELETE' }),

  // Facturas (MySQL)
  createInvoice: (payload) => request('/invoices', json('POST', payload)),
  getInvoiceConfig: () => request('/invoices/config'),
  getInvoices: () => request('/invoices'),
  getInvoice: (id) => request(`/invoices/${id}`),

  // Backup analítico: tabla de hechos en Apache Druid (WSL2)
  getBackups: () => request('/backups'),
  getBackupAnalytics: () => request('/backups/analytics'),
  regenerateFacts: () => request('/backups/regenerate', { method: 'POST' })
};
