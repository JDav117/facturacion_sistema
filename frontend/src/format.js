const currency = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0
});

export const money = (value) => currency.format(Number(value ?? 0));

export const dateTime = (value) =>
  value
    ? new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' })
    : '—';

export const PLACEHOLDER_IMG = '/img/productos/placeholder.svg';
