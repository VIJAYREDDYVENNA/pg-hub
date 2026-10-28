import dayjs from 'dayjs';
import { expenseApi } from '../../services/endpoints';
import { label } from '../../utils/format';
import { methodLabel } from '../payments/financeUtils';

/** Must match ExpenseService.CATEGORIES on the backend. */
export const CATEGORIES = ['ELECTRICITY', 'WATER', 'SALARY', 'MAINTENANCE', 'FOOD', 'INTERNET', 'RENT', 'SUPPLIES', 'TAXES', 'OTHER'];
export const categoryOptions = CATEGORIES.map((c) => ({ value: c, label: label(c) }));

export const CATEGORY_ICONS = {
  ELECTRICITY: 'bi-lightning-charge-fill', WATER: 'bi-droplet', SALARY: 'bi-people', MAINTENANCE: 'bi-tools', FOOD: 'bi-cup-hot',
  INTERNET: 'bi-wifi', RENT: 'bi-building', SUPPLIES: 'bi-box-seam', TAXES: 'bi-bank', OTHER: 'bi-three-dots',
};

/** Badge colours per category (text colour + tinted background). */
export const CATEGORY_STYLE = {
  INTERNET: { fg: '#2563EB', bg: '#E3EEFF' },
  ELECTRICITY: { fg: '#B45309', bg: '#FFF2D2' },
  FOOD: { fg: '#15803D', bg: '#DDF7E6' },
  SALARY: { fg: '#7C3AED', bg: '#EFE7FF' },
  WATER: { fg: '#0369A1', bg: '#DFF1FF' },
  MAINTENANCE: { fg: '#C2410C', bg: '#FFEDE1' },
  RENT: { fg: '#0F766E', bg: '#D9F5F1' },
  SUPPLIES: { fg: '#A16207', bg: '#FDF1CF' },
  TAXES: { fg: '#475569', bg: '#EAEFF5' },
  OTHER: { fg: '#475569', bg: '#EAEFF5' },
};

/** Hostel colours for the donut (orange, blue, yellow as in the design, then extras). */
export const HOSTEL_COLORS = ['#FF5A0A', '#2F80ED', '#F5B51B', '#16A34A', '#8B5CF6', '#EF4444', '#14B8A6', '#94A3B8'];

const PAGE = 100;
const MAX_ROWS = 1000;

/** Loads every expense matching the server-side filters (hostel, category, dates), 100 per request, up to 1000 rows. */
export async function fetchAllExpenses(params) {
  const first = await expenseApi.list({ ...params, page: 0, size: PAGE });
  let rows = first.content || [];
  const totalPages = first.totalPages || 1;
  for (let p = 1; p < totalPages && rows.length < MAX_ROWS; p += 1) {
    // eslint-disable-next-line no-await-in-loop
    const next = await expenseApi.list({ ...params, page: p, size: PAGE });
    rows = rows.concat(next.content || []);
  }
  return { rows: rows.slice(0, MAX_ROWS), truncated: (first.totalElements || 0) > MAX_ROWS };
}

const fmt = (d) => d.format('YYYY-MM-DD');

/** The period of equal length that ends the day before `from`. */
export function previousPeriod(from, to) {
  const f = dayjs(from);
  const days = dayjs(to).diff(f, 'day') + 1;
  const prevTo = f.subtract(1, 'day');
  return { from: fmt(prevTo.subtract(days - 1, 'day')), to: fmt(prevTo) };
}

/** This month so far and the whole previous month. */
export function monthPeriods(todayIso) {
  const t = dayjs(todayIso);
  const lastMonth = t.subtract(1, 'month');
  return {
    thisMonth: { from: fmt(t.startOf('month')), to: todayIso },
    lastMonth: { from: fmt(lastMonth.startOf('month')), to: fmt(lastMonth.endOf('month')) },
  };
}

export const sumAmounts = (list) => (list || []).reduce((s, x) => s + Math.round(Number(x.amount) * 100), 0) / 100;

/** Percentage change, or null when there is nothing to compare against. */
export const pctChange = (current, previous) => (previous > 0 ? ((current - previous) / previous) * 100 : null);

const csvCell = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;

export function downloadCsv(rows, hostelName, filename = 'expenses.csv') {
  const head = ['Date', 'Expense', 'Category', 'Hostel', 'Paid by', 'Vendor', 'Reference', 'Amount'];
  const body = rows.map((r) => [r.expenseDate, r.description, label(r.category), hostelName(r.hostelId),
    methodLabel(r.paymentMethod), r.vendor || '', r.reference || '', r.amount]);
  const text = [head, ...body].map((l) => l.map(csvCell).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob(['﻿', text], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
