import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import useApi from '../../hooks/useApi';
import { invoiceApi, paymentApi, residentApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import StatusBadge from '../../components/common/StatusBadge';
import { ErrorState, LoadingState } from '../../components/common/States';
import ResidentPicker from '../../components/payments/ResidentPicker';
import { METHOD_ICONS, fromPaise, toPaise } from '../../components/payments/financeUtils';
import { date, label, money, shortDate, today } from '../../utils/format';
import { INVOICE_STATUS, PAYMENT_METHODS } from '../../utils/status';
import Icon from '../../components/common/Icon';

/** Resident shape for the picker, built from a full profile. */
const pickerValue = (p) => p && ({
  id: p.id,
  fullName: p.fullName,
  mobile: p.mobile,
  hostelName: p.hostelName,
  roomNumber: p.currentAdmission?.currentBed?.roomNumber,
  bedLabel: p.currentAdmission?.currentBed?.bedLabel,
});

function BalanceStrip({ balance }) {
  const items = [
    { label: 'Outstanding', value: balance?.outstanding, cls: Number(balance?.outstanding) > 0 ? 'text-danger' : '' },
    { label: 'Overdue', value: balance?.overdue, cls: Number(balance?.overdue) > 0 ? 'text-danger' : '' },
    { label: 'Advance credit', value: balance?.credit, cls: '' },
    { label: 'Deposit held', value: balance?.depositHeld, cls: '' },
  ];
  return (
    <div className="row g-2 mb-3">
      {items.map((i) => (
        <div className="col-6 col-md-3" key={i.label}>
          <div className="border rounded-3 p-2 h-100">
            <div className="small text-muted-hh">{i.label}</div>
            <div className={`fw-bold ${i.cls}`}>{money(i.value)}</div>
          </div>
        </div>
      ))}
    </div>
  );
}

export default function CollectPaymentPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const [params, setParams] = useSearchParams();
  const residentId = params.get('residentId') ? Number(params.get('residentId')) : null;
  const [mode, setMode] = useState('AUTO');
  // One key per resident selection: a double click, or a retry after a network error, returns the first receipt
  // instead of recording the money twice.
  const idempotencyKey = useMemo(
    () => (window.crypto?.randomUUID ? window.crypto.randomUUID() : `k${Date.now()}${Math.random().toString(36).slice(2)}`),
    [residentId],
  );
  const [picks, setPicks] = useState({});
  const [allocError, setAllocError] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => { document.title = 'Collect payment · HostelHub'; }, []);

  const profileQ = useApi(() => residentApi.get(residentId), [residentId], { enabled: !!residentId });
  const invoicesQ = useApi(() => invoiceApi.list({ residentId, status: 'OPEN', size: 50 }), [residentId], { enabled: !!residentId });
  // Ignore data still belonging to a previously selected resident.
  const profile = residentId && profileQ.data?.id === residentId ? profileQ.data : null;
  const invoices = useMemo(() => ((residentId && invoicesQ.data?.content) || []).filter((i) => i.residentId === residentId),
    [residentId, invoicesQ.data]);

  const { register, handleSubmit, reset, watch, setValue, setError: setFieldError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { amount: '', paymentDate: today(), method: 'UPI', transactionReference: '', notes: '', sendWhatsApp: false },
  });
  const method = watch('method');
  const amount = watch('amount');

  // New resident → default amount = total outstanding, WhatsApp only if opted in, reset invoice choices.
  useEffect(() => {
    if (!profile || profile.id !== residentId) return;
    const due = toPaise(profile.balance?.outstanding);
    reset({
      amount: due > 0 ? fromPaise(due) : '',
      paymentDate: today(),
      method: 'UPI',
      transactionReference: '',
      notes: '',
      sendWhatsApp: !!profile.whatsappOptIn,
    });
    setPicks({});
    setMode('AUTO');
    setAllocError(null);
    setError(null);
  }, [profile, residentId, reset]);

  const choose = (r) => {
    setParams((p) => {
      const n = new URLSearchParams(p);
      if (r) n.set('residentId', r.id); else n.delete('residentId');
      return n;
    }, { replace: true });
  };

  const togglePick = (inv) => setPicks((p) => {
    const n = { ...p };
    if (n[inv.id]) delete n[inv.id]; else n[inv.id] = fromPaise(toPaise(inv.balance));
    return n;
  });

  const pickedPaise = Object.values(picks).reduce((s, v) => s + (toPaise(v) || 0), 0);
  const amountPaise = toPaise(amount);

  const validateAllocations = () => {
    const entries = Object.entries(picks);
    if (!entries.length) return 'Choose at least one invoice, or switch to automatic allocation.';
    for (const [id, v] of entries) {
      const inv = invoices.find((i) => String(i.id) === String(id));
      const p = toPaise(v);
      if (!(p > 0)) return `Enter an amount for ${inv?.invoiceNumber || 'each selected invoice'}.`;
      if (inv && p > toPaise(inv.balance)) return `${inv.invoiceNumber}: amount is more than its balance of ${money(inv.balance)}.`;
    }
    if (pickedPaise > amountPaise) return `Invoice amounts add up to ${money(fromPaise(pickedPaise))}, more than the ${money(amount)} received.`;
    return null;
  };

  const submit = async (v) => {
    setError(null);
    setAllocError(null);
    let allocations = null;
    if (mode === 'CHOOSE') {
      const msg = validateAllocations();
      if (msg) { setAllocError(msg); return; }
      allocations = Object.entries(picks).map(([invoiceId, a]) => ({ invoiceId: Number(invoiceId), amount: Number(a) }));
    }
    try {
      const p = await paymentApi.collect({
        idempotencyKey,
        residentId,
        amount: Number(v.amount),
        paymentDate: v.paymentDate,
        method: v.method,
        transactionReference: v.transactionReference?.trim() || null,
        notes: v.notes?.trim() || null,
        allocations,
        sendWhatsApp: !!v.sendWhatsApp && !!profile?.whatsappOptIn,
      });
      notify(`Payment of ${money(p.amount)} recorded. Receipt ${p.receiptNumber}.`);
      navigate(`/payments/${p.id}`);
    } catch (e) {
      if (!applyServerErrors(e, setFieldError)) setError(errorMessage(e));
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const loadingResident = residentId && !profile && !profileQ.error;
  const extraPaise = mode === 'AUTO' && profile ? amountPaise - toPaise(profile.balance?.outstanding) : 0;

  return (
    <>
      <PageHeader title="Collect payment" icon="bi-cash-coin" subtitle="Record money you have received from a resident"
        actions={<Link to="/payments?tab=payments" className="btn btn-light"><Icon className="bi bi-arrow-left me-1" />Payments</Link>} />

      <div className="alert alert-warning d-flex gap-2 align-items-start" role="note">
        <Icon className="bi bi-shield-check fs-5" aria-hidden="true" />
        <div><strong>Only record a payment after the money has actually been received</strong> — cash in hand, or the UPI / bank credit confirmed in your account.
          A reminder or a WhatsApp message is never a payment.</div>
      </div>

      {error && <div className="alert alert-danger" role="alert">{error}</div>}

      <div className="row g-3">
        <div className="col-lg-7">
          <section className="hh-card mb-3" aria-labelledby="resident-step">
            <h2 className="hh-card-title mb-3" id="resident-step"><span className="badge rounded-pill bg-primary-light text-primary-hh me-2">1</span>Resident</h2>
            <ResidentPicker id="collect-resident" label={null} value={pickerValue(profile)} onChange={choose} autoFocus={!residentId} />
            {loadingResident && <LoadingState compact label="Loading resident…" />}
            {residentId && profileQ.error && <ErrorState compact error={profileQ.error} onRetry={profileQ.reload} />}
            {profile && <BalanceStrip balance={profile.balance} />}
            {profile && (
              <>
                <h3 className="h6 fw-bold mb-2">Open invoices</h3>
                {invoicesQ.loading && !invoicesQ.data && <LoadingState compact label="Loading invoices…" />}
                {invoicesQ.error && <ErrorState compact error={invoicesQ.error} onRetry={invoicesQ.reload} />}
                {invoicesQ.data && invoices.length === 0 && (
                  <div className="text-muted-hh small mb-2"><Icon className="bi bi-check2-circle me-1 text-success" />No unpaid invoices. Any amount received is kept as advance credit.</div>
                )}
                {invoices.length > 0 && (
                  <ul className="list-unstyled mb-0">
                    {invoices.map((inv) => (
                      <li key={inv.id} className="hh-feed-item">
                        <div className="min-w-0 flex-grow-1">
                          <Link to={`/invoices/${inv.id}`} className="fw-semibold">{inv.invoiceNumber}</Link>
                          <span className="small text-muted-hh ms-2">{label(inv.invoiceType)}</span>
                          <div className="small text-muted-hh">
                            {inv.periodStart ? `${shortDate(inv.periodStart)} – ${shortDate(inv.periodEnd)} · ` : ''}due {date(inv.dueDate)}
                            {inv.daysOverdue > 0 && <span className="text-danger"> · {inv.daysOverdue} day(s) late</span>}
                          </div>
                        </div>
                        <div className="text-end">
                          <div className="fw-bold">{money(inv.balance)}</div>
                          <StatusBadge map={INVOICE_STATUS} value={inv.status} />
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
            {!residentId && <p className="small text-muted-hh mb-0">Search for the resident who paid. Their dues and open invoices will appear here.</p>}
          </section>
        </div>

        <div className="col-lg-5">
          <form className="hh-card" onSubmit={handleSubmit(submit)} noValidate aria-labelledby="payment-step">
            <h2 className="hh-card-title mb-3" id="payment-step"><span className="badge rounded-pill bg-primary-light text-primary-hh me-2">2</span>Payment details</h2>
            <fieldset disabled={!profile || isSubmitting}>
              <div className="row g-2">
                <div className="col-sm-6">
                  <FormField label="Amount received (₹)" name="amount" type="number" inputMode="decimal" step="0.01" min="0.01"
                    className="mb-2" register={register} errors={errors}
                    rules={{ required: 'Enter the amount received', validate: (x) => toPaise(x) >= 1 || 'Amount must be more than zero' }} />
                </div>
                <div className="col-sm-6">
                  <FormField label="Date received" name="paymentDate" type="date" max={today()} className="mb-2" register={register} errors={errors}
                    rules={{ required: 'Choose the date', validate: (x) => x <= today() || 'Payment date cannot be in the future' }} />
                </div>
              </div>
              {extraPaise > 0 && (
                <div className="form-text mb-2"><Icon className="bi bi-info-circle me-1" />{money(fromPaise(extraPaise))} more than the dues — kept as advance credit.</div>
              )}

              <div className="mb-3">
                <div className="form-label">Payment method<span className="text-danger ms-1" aria-hidden="true">*</span></div>
                <div className="row g-2" role="radiogroup" aria-label="Payment method">
                  {PAYMENT_METHODS.map((m) => (
                    <div className="col-4" key={m.value}>
                      <button type="button" role="radio" aria-checked={method === m.value}
                        className={`btn w-100 py-2 d-flex flex-column align-items-center gap-1 ${method === m.value ? 'btn-primary' : 'btn-light'}`}
                        style={{ minHeight: 64 }} onClick={() => setValue('method', m.value, { shouldDirty: true })}>
                        <Icon className={`bi ${METHOD_ICONS[m.value]} fs-5`} aria-hidden="true" />
                        <span className="small fw-semibold">{m.label}</span>
                      </button>
                    </div>
                  ))}
                </div>
                <input type="hidden" {...register('method', { required: true })} />
              </div>

              <FormField label={method === 'CASH' ? 'Reference (optional)' : 'Transaction reference'} name="transactionReference"
                register={register} errors={errors} rules={{ maxLength: { value: 100, message: 'Keep it under 100 characters' } }}
                placeholder={method === 'UPI' ? 'UPI ref / UTR number' : method === 'BANK_TRANSFER' ? 'UTR / bank reference' : ''}
                help={method !== 'CASH' ? 'Recommended: add the UPI / bank reference so the payment can be matched to your statement.' : undefined} />

              <fieldset className="mb-3">
                <legend className="form-label fs-6">Apply this payment to</legend>
                <div className="form-check mb-1">
                  <input className="form-check-input" type="radio" id="alloc-auto" checked={mode === 'AUTO'} onChange={() => { setMode('AUTO'); setAllocError(null); }} />
                  <label className="form-check-label" htmlFor="alloc-auto">Oldest dues first (automatic)</label>
                </div>
                <div className="form-check">
                  <input className="form-check-input" type="radio" id="alloc-choose" checked={mode === 'CHOOSE'}
                    disabled={invoices.length === 0} onChange={() => setMode('CHOOSE')} />
                  <label className="form-check-label" htmlFor="alloc-choose">Choose invoices</label>
                </div>
              </fieldset>

              {mode === 'CHOOSE' && (
                <div className="border rounded-3 p-2 mb-3">
                  {invoices.map((inv) => {
                    const checked = picks[inv.id] !== undefined;
                    return (
                      <div key={inv.id} className="d-flex align-items-center gap-2 py-2 border-bottom flex-wrap">
                        <input className="form-check-input mt-0 flex-shrink-0" type="checkbox" id={`pick-${inv.id}`}
                          style={{ width: 22, height: 22 }} checked={checked} onChange={() => togglePick(inv)} />
                        <label htmlFor={`pick-${inv.id}`} className="flex-grow-1 min-w-0 small">
                          <span className="fw-semibold">{inv.invoiceNumber}</span> · {label(inv.invoiceType)}
                          <div className="text-muted-hh">Balance {money(inv.balance)}</div>
                        </label>
                        <input type="number" inputMode="decimal" step="0.01" min="0.01" max={inv.balance}
                          className="form-control form-control-sm" style={{ width: 120 }} aria-label={`Amount for ${inv.invoiceNumber}`}
                          disabled={!checked} value={checked ? picks[inv.id] : ''}
                          onChange={(e) => setPicks((p) => ({ ...p, [inv.id]: e.target.value }))} />
                      </div>
                    );
                  })}
                  <div className="d-flex justify-content-between small pt-2">
                    <span className="text-muted-hh">Allocated</span>
                    <span className={pickedPaise > amountPaise ? 'text-danger fw-bold' : 'fw-semibold'}>
                      {money(fromPaise(pickedPaise))} of {money(amount || 0)}
                    </span>
                  </div>
                  {amountPaise > pickedPaise && pickedPaise > 0 && (
                    <div className="small text-muted-hh">The remaining {money(fromPaise(amountPaise - pickedPaise))} is kept as advance credit.</div>
                  )}
                  {allocError && <div className="small text-danger mt-1" role="alert">{allocError}</div>}
                </div>
              )}

              <FormField label="Notes (optional)" name="notes" as="textarea" register={register} errors={errors}
                rules={{ maxLength: { value: 500, message: 'Keep notes under 500 characters' } }} />

              <div className="form-check mb-3">
                <input className="form-check-input" type="checkbox" id="send-wa" {...register('sendWhatsApp')}
                  disabled={!profile?.whatsappOptIn} />
                <label className="form-check-label" htmlFor="send-wa">
                  <Icon className="bi bi-whatsapp me-1 text-success" aria-hidden="true" />Send payment confirmation on WhatsApp — only if the resident opted in
                </label>
                {profile && !profile.whatsappOptIn && (
                  <div className="form-text">{profile.fullName} has not opted in to WhatsApp messages, so no confirmation will be sent.</div>
                )}
              </div>

              <button type="submit" className="btn btn-primary btn-lg w-100" disabled={!profile || isSubmitting}>
                {isSubmitting ? <span className="spinner-border spinner-border-sm me-2" /> : <Icon className="bi bi-check2-circle me-2" />}
                Record {amount ? money(amount) : 'payment'} received
              </button>
              {!profile && <div className="form-text text-center">Select a resident first.</div>}
            </fieldset>
          </form>
        </div>
      </div>
    </>
  );
}
