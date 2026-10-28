import { label, money } from '../../utils/format';
import { PAYMENT_METHODS } from '../../utils/status';

/** Theme colours used by finance charts (match theme.css tokens). */
export const CHART_COLORS = {
  orange: '#FF5A0A',
  green: '#16A34A',
  red: '#EF4444',
  yellow: '#F5B51B',
  gray: '#94A3B8',
  grid: '#F1E4D8',
  axis: '#687386',
};

/** Invoice statuses that can still receive money, reminders and adjustments. */
export const OPEN_INVOICE_STATUSES = ['PENDING', 'PARTIALLY_PAID', 'OVERDUE'];
export const isOpenInvoice = (inv) => !!inv && OPEN_INVOICE_STATUSES.includes(inv.status);

/** Invoice list filter options (the backend understands the two virtual filters OPEN and DUE_TODAY). */
export const INVOICE_FILTERS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'OPEN', label: 'Open (unpaid)' },
  { value: 'DUE_TODAY', label: 'Due today' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'PARTIALLY_PAID', label: 'Partially paid' },
  { value: 'PAID', label: 'Paid' },
  { value: 'WAIVED', label: 'Waived' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export const INVOICE_TYPES = [
  { value: 'ALL', label: 'All types' },
  { value: 'RENT', label: 'Rent' },
  { value: 'DEPOSIT', label: 'Deposit' },
  { value: 'OTHER', label: 'Other charges' },
];

export const METHOD_ICONS = {
  UPI: 'bi-phone',
  CASH: 'bi-cash-stack',
  BANK_TRANSFER: 'bi-bank',
  CARD: 'bi-credit-card',
  OTHER: 'bi-three-dots',
  DEPOSIT_ADJUSTMENT: 'bi-safe',
  ONLINE: 'bi-globe2',
};

/** Human label for a payment method, including the system-only DEPOSIT_ADJUSTMENT. */
export const methodLabel = (m) => {
  if (m === 'DEPOSIT_ADJUSTMENT') return 'Deposit adjustment';
  return PAYMENT_METHODS.find((x) => x.value === m)?.label || label(m);
};

export const periodLabel = (start, end, fmt) => {
  if (!start && !end) return '';
  return `${fmt(start)} – ${fmt(end)}`;
};

/** Money input values → integer paise for safe comparisons (display still uses API values). */
export const toPaise = (v) => {
  const n = Number(v);
  return Number.isFinite(n) ? Math.round(n * 100) : NaN;
};
export const fromPaise = (p) => (p / 100).toFixed(2);

const compact = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', notation: 'compact', maximumFractionDigits: 1 });
/** Short currency for chart axes (₹12K, ₹1.2L style compact notation). */
export const compactMoney = (v) => (v === null || v === undefined ? '' : compact.format(Number(v)));

/** Recharts tooltip formatter for money series. */
export const moneyTooltip = (value, name) => [money(value), name];

/** Builds a toast for the result of a WhatsApp invoice reminder (a reminder is never a payment). */
export function reminderToast(msg, notify, who) {
  const name = who || msg?.residentName || 'the resident';
  if (!msg) return;
  if (msg.status === 'SKIPPED_NO_CONSENT') {
    notify(`Reminder not sent: ${name} has not opted in to WhatsApp messages.`, 'warning');
  } else if (msg.status === 'FAILED') {
    notify(`Reminder could not be sent${msg.errorMessage ? `: ${msg.errorMessage}` : '.'}`, 'danger');
  } else {
    notify(`Reminder ${label(msg.status).toLowerCase()} for ${name}${msg.simulated ? ' (simulated – no real WhatsApp message was sent)' : ''}.`);
  }
}
