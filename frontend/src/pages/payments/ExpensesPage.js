import { useCallback, useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import dayjs from 'dayjs';
import useApi from '../../hooks/useApi';
import { expenseApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import Modal from '../../components/common/Modal';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import { ErrorState } from '../../components/common/States';
import ExpenseSummaryCards from '../../components/expenses/ExpenseSummaryCards';
import ExpenseFilters from '../../components/expenses/ExpenseFilters';
import ExpenseTable from '../../components/expenses/ExpenseTable';
import { CategoryCard, HostelCard } from '../../components/expenses/ExpenseAnalytics';
import { categoryOptions, fetchAllExpenses, monthPeriods, pctChange, previousPeriod, sumAmounts } from '../../components/expenses/expenseUtils';
import { toPaise } from '../../components/payments/financeUtils';
import { money, today } from '../../utils/format';
import { PAYMENT_METHODS } from '../../utils/status';
import Icon from '../../components/common/Icon';

function ExpenseModal({ show, expense, defaultHostelId, onClose, onSaved }) {
  const { hostels } = useHostels();
  const { notify } = useToast();
  const [error, setError] = useState(null);
  const editing = !!expense?.id;
  const { register, handleSubmit, reset, setError: setFieldError, formState: { errors, isSubmitting } } = useForm();

  useEffect(() => {
    if (!show) return;
    setError(null);
    reset(expense?.id ? {
      hostelId: String(expense.hostelId), category: expense.category, description: expense.description,
      amount: expense.amount, expenseDate: expense.expenseDate, paymentMethod: expense.paymentMethod,
      vendor: expense.vendor || '', reference: expense.reference || '',
    } : {
      hostelId: String(defaultHostelId || hostels[0]?.id || ''), category: 'ELECTRICITY', description: '', amount: '',
      expenseDate: today(), paymentMethod: 'UPI', vendor: '', reference: '',
    });
  }, [show, expense, defaultHostelId, hostels, reset]);

  const submit = async (v) => {
    setError(null);
    const body = {
      hostelId: Number(v.hostelId), category: v.category, description: v.description.trim(), amount: Number(v.amount),
      expenseDate: v.expenseDate, paymentMethod: v.paymentMethod, vendor: v.vendor?.trim() || null,
      reference: v.reference?.trim() || null, maintenanceRequestId: expense?.maintenanceRequestId ?? null,
    };
    try {
      if (editing) await expenseApi.update(expense.id, body); else await expenseApi.create(body);
      notify(editing ? 'Expense updated.' : `Expense of ${money(v.amount)} added.`);
      onSaved();
    } catch (e) {
      if (!applyServerErrors(e, setFieldError)) setError(errorMessage(e));
    }
  };

  return (
    <Modal show={show} title={editing ? 'Edit expense' : 'Add expense'} onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="expense-form" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{editing ? 'Save changes' : 'Add expense'}
        </button>
      </>}>
      <form id="expense-form" onSubmit={handleSubmit(submit)} noValidate>
        {error && <div className="alert alert-danger py-2">{error}</div>}
        <FormField label="Hostel" name="hostelId" as="select" register={register} errors={errors}
          rules={{ required: 'Choose the hostel' }} options={hostels.map((h) => ({ value: String(h.id), label: h.name }))} />
        <div className="row g-2">
          <div className="col-sm-6">
            <FormField label="Category" name="category" as="select" register={register} errors={errors}
              rules={{ required: 'Choose a category' }} options={categoryOptions} />
          </div>
          <div className="col-sm-6">
            <FormField label="Amount (₹)" name="amount" type="number" inputMode="decimal" step="0.01" min="0.01" register={register} errors={errors}
              rules={{ required: 'Enter the amount', validate: (x) => toPaise(x) >= 1 || 'Amount must be more than zero' }} />
          </div>
        </div>
        <FormField label="Description" name="description" register={register} errors={errors}
          rules={{ required: 'Describe the expense', maxLength: { value: 500, message: 'Keep it under 500 characters' } }}
          placeholder="e.g. Electricity bill – September" />
        <div className="row g-2">
          <div className="col-sm-6">
            <FormField label="Date paid" name="expenseDate" type="date" max={today()} register={register} errors={errors}
              rules={{ required: 'Choose the date', validate: (x) => x <= today() || 'Expense date cannot be in the future' }} />
          </div>
          <div className="col-sm-6">
            <FormField label="Paid by" name="paymentMethod" as="select" register={register} errors={errors}
              rules={{ required: 'Choose how it was paid' }} options={PAYMENT_METHODS} />
          </div>
          <div className="col-sm-6">
            <FormField label="Vendor (optional)" name="vendor" register={register} errors={errors}
              rules={{ maxLength: { value: 150, message: 'Too long' } }} />
          </div>
          <div className="col-sm-6">
            <FormField label="Bill / reference (optional)" name="reference" register={register} errors={errors}
              rules={{ maxLength: { value: 100, message: 'Too long' } }} />
          </div>
        </div>
        <p className="small text-muted-hh mb-0"><Icon className="bi bi-info-circle me-1" />Expenses cannot be deleted so your financial history stays complete. Correct mistakes by editing the entry.</p>
      </form>
    </Modal>
  );
}

const defaultFilters = (hostelId) => ({
  hostelId: hostelId ? String(hostelId) : '', category: 'ALL', from: dayjs().startOf('month').format('YYYY-MM-DD'), to: today(),
  q: '', method: 'ALL', minAmount: '', maxAmount: '',
});

export default function ExpensesPage() {
  const { hostels, selectedHostelId, selectHostel } = useHostels();
  const [draft, setDraft] = useState(() => defaultFilters(selectedHostelId));
  const [applied, setApplied] = useState(draft);
  const [editing, setEditing] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const draftRangeError = draft.from && draft.to && draft.from > draft.to ? 'The start date must be on or before the end date.' : null;
  const rangeOk = !(applied.from && applied.to && applied.from > applied.to);
  const hostelId = applied.hostelId ? Number(applied.hostelId) : undefined;
  const catParam = applied.category === 'ALL' ? undefined : applied.category;

  useEffect(() => { document.title = 'Expenses · HostelHub'; }, []);

  // Every expense matching the server-side filters; search, payment method and amount are applied below.
  const all = useApi(() => fetchAllExpenses({ hostelId, category: catParam, from: applied.from, to: applied.to }),
    [hostelId, catParam, applied.from, applied.to, refreshKey], { enabled: rangeOk });

  const rows = useMemo(() => {
    const term = applied.q.trim().toLowerCase();
    const min = applied.minAmount === '' ? null : Number(applied.minAmount);
    const max = applied.maxAmount === '' ? null : Number(applied.maxAmount);
    const name = (id) => hostels.find((h) => h.id === id)?.name || '';
    return (all.data?.rows || []).filter((r) => (applied.method === 'ALL' || r.paymentMethod === applied.method)
      && (min === null || Number(r.amount) >= min) && (max === null || Number(r.amount) <= max)
      && (!term || [r.description, r.vendor, r.reference, r.category, name(r.hostelId)].some((v) => (v || '').toLowerCase().includes(term))));
  }, [all.data, applied, hostels]);

  const refined = applied.q.trim() !== '' || applied.method !== 'ALL' || applied.minAmount !== '' || applied.maxAmount !== '';

  // Comparison figures: previous period, this month and last month (same hostel and category).
  const compare = useApi(async () => {
    const prev = previousPeriod(applied.from, applied.to);
    const { thisMonth, lastMonth } = monthPeriods(today());
    const base = { hostelId };
    const totalOf = (list) => sumAmounts(catParam ? (list || []).filter((c) => c.category === catParam) : list);
    const [prevCat, monthCat, lastCat, prevList, monthList] = await Promise.all([
      expenseApi.byCategory({ ...base, from: prev.from, to: prev.to }),
      expenseApi.byCategory({ ...base, ...thisMonth }),
      expenseApi.byCategory({ ...base, from: lastMonth.from, to: lastMonth.to }),
      expenseApi.list({ ...base, category: catParam, from: prev.from, to: prev.to, page: 0, size: 1 }),
      expenseApi.list({ ...base, category: catParam, ...thisMonth, page: 0, size: 1 }),
    ]);
    return {
      prevTotal: totalOf(prevCat), prevCount: prevList.totalElements || 0,
      thisMonth: totalOf(monthCat), lastMonth: totalOf(lastCat), newThisMonth: monthList.totalElements || 0,
    };
  }, [hostelId, catParam, applied.from, applied.to, refreshKey], { enabled: rangeOk });

  const summary = useMemo(() => {
    if (!all.data || !compare.data) return null;
    const total = sumAmounts(rows);
    const count = rows.length;
    const average = count ? total / count : 0;
    const c = compare.data;
    const prevAverage = c.prevCount ? c.prevTotal / c.prevCount : 0;
    return {
      total, count, average: Math.round(average), thisMonth: c.thisMonth, newThisMonth: c.newThisMonth,
      // Search / payment-method / amount filters only exist on this side, so no like-for-like comparison is possible.
      totalChange: refined ? null : pctChange(total, c.prevTotal),
      monthChange: pctChange(c.thisMonth, c.lastMonth),
      averageChange: refined ? null : pctChange(average, prevAverage),
    };
  }, [all.data, compare.data, rows, refined]);

  const hostelName = useCallback((id) => hostels.find((h) => h.id === id)?.name || '—', [hostels]);

  const apply = () => {
    if (draftRangeError) return;
    setApplied(draft);
    selectHostel(draft.hostelId ? Number(draft.hostelId) : null);
  };
  const reset = () => {
    const fresh = defaultFilters(null);
    setDraft(fresh);
    setApplied(fresh);
    selectHostel(null);
  };
  const filtered = refined || applied.category !== 'ALL' || !!applied.hostelId;

  return (
    <>
      <PageHeader title="Expenses" icon="bi-wallet" subtitle="Money spent on running your hostels"
        actions={<button type="button" className="btn btn-primary" onClick={() => setEditing({})} disabled={!hostels.length}>
          <Icon className="bi bi-plus-lg me-1" />Add expense
        </button>} />

      <ExpenseSummaryCards s={summary} loading={!summary && !all.error} />

      <div className="hh-exp-layout">
        <div className="hh-exp-main">
          <ExpenseFilters hostels={hostels} draft={draft} setDraft={setDraft} onApply={apply} onReset={reset} rangeError={draftRangeError} />
          {all.error ? <ErrorState error={all.error} onRetry={all.reload} /> : (
            <>
              {all.data?.truncated && (
                <div className="alert alert-warning py-2 small">Showing the first 1,000 expenses. Narrow the date range or filters to see the rest.</div>
              )}
              <ExpenseTable rows={rows} loading={all.loading && !all.data} hostelName={hostelName} filtered={filtered}
                onEdit={setEditing} onRefresh={() => setRefreshKey((k) => k + 1)} />
            </>
          )}
        </div>
        <aside className="hh-exp-aside" aria-label="Expense analytics">
          <CategoryCard rows={rows} from={applied.from} to={applied.to} />
          <HostelCard rows={rows} hostelName={hostelName} from={applied.from} to={applied.to} />
        </aside>
      </div>

      <div className="hh-exp-notice" role="note">
        <Icon className="bi bi-info-circle" aria-hidden="true" />
        <span>Expenses are never deleted so your financial history stays complete. Use Edit to correct an entry.</span>
      </div>

      <ExpenseModal show={!!editing} expense={editing} defaultHostelId={applied.hostelId} onClose={() => setEditing(null)}
        onSaved={() => { setEditing(null); setRefreshKey((k) => k + 1); }} />
    </>
  );
}
