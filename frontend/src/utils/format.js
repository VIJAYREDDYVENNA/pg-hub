import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2, minimumFractionDigits: 0 });
const num = new Intl.NumberFormat('en-IN');

/** Money values arrive from the API as decimal numbers/strings; they are only formatted here, never computed. */
export const money = (v) => (v === null || v === undefined || v === '' ? '—' : inr.format(Number(v)));
export const number = (v) => (v === null || v === undefined ? '—' : num.format(v));
export const date = (v) => (v ? dayjs(v).format('DD MMM YYYY') : '—');
export const shortDate = (v) => (v ? dayjs(v).format('DD MMM') : '—');
export const dateTime = (v) => (v ? dayjs(v).format('DD MMM YYYY, hh:mm A') : '—');
export const fromNow = (v) => (v ? dayjs(v).fromNow() : '');
export const today = () => dayjs().format('YYYY-MM-DD');
export const isoDate = (d) => dayjs(d).format('YYYY-MM-DD');
export const addDays = (n, from) => dayjs(from || undefined).add(n, 'day').format('YYYY-MM-DD');
export const label = (s) => (s ? String(s).replace(/_/g, ' ').toLowerCase().replace(/(^|\s)\S/g, (c) => c.toUpperCase()) : '');
export const percent = (v) => (v === null || v === undefined ? '—' : `${Number(v).toFixed(Number(v) % 1 === 0 ? 0 : 1)}%`);

/** wa.me link to open a chat in the user's own WhatsApp app (no message is sent by the system). */
export const whatsappLink = (mobile, text = '') => {
  const digits = String(mobile || '').replace(/\D/g, '');
  const full = digits.length === 10 ? `91${digits}` : digits;
  return `https://wa.me/${full}${text ? `?text=${encodeURIComponent(text)}` : ''}`;
};
export const telLink = (mobile) => `tel:${String(mobile || '').replace(/[^\d+]/g, '')}`;
