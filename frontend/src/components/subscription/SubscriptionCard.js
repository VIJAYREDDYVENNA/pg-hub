import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import Icon from '../common/Icon';
import Modal from '../common/Modal';
import StatusBadge from '../common/StatusBadge';
import FormField, { applyServerErrors } from '../common/FormField';
import { ErrorState, LoadingState } from '../common/States';
import LimitFields from './LimitFields';
import { EntitlementList } from './SubscriptionFields';
import { SUBSCRIPTION_STATE } from '../../utils/status';
import { LIMIT_FIELDS, planDefaults, usageLevel, validateSubscriptionForm } from '../../utils/subscriptionLogic.mjs';
import { date, dateTime, label, money, today } from '../../utils/format';

const STATUS_OPTIONS = [
  { value: 'TRIAL', label: 'Trial' }, { value: 'ACTIVE', label: 'Active' },
  { value: 'SUSPENDED', label: 'Suspended (billing hold)' }, { value: 'CANCELLED', label: 'Cancelled' },
];
const METHODS = ['CASH', 'UPI', 'BANK_TRANSFER', 'CARD', 'OTHER'].map((m) => ({ value: m, label: label(m) }));
const LEVEL_CLASS = { ok: '', warn: 'text-warning-emphasis', full: 'text-danger fw-semibold', over: 'text-danger fw-semibold' };

function EditModal({ ownerId, sub, plans, features, onClose, onSaved }) {
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const form = useForm();
  const { register, handleSubmit, reset, watch, setValue, setError, formState: { errors, isSubmitting } } = form;

  useEffect(() => {
    reset({
      planId: String(sub.plan.id), status: sub.status, startsOn: sub.startsOn, trialEndsOn: sub.trialEndsOn || '',
      periodEndsOn: sub.periodEndsOn || '', graceDays: String(sub.graceDays),
      maxHostels: String(sub.effectiveLimits.maxHostels), maxBedsPerHostel: String(sub.effectiveLimits.maxBedsPerHostel),
      maxManagers: String(sub.effectiveLimits.maxManagers),
      customMonthlyPrice: sub.effectiveMonthlyPrice == null ? '' : String(sub.effectiveMonthlyPrice), reason: '',
    });
  }, [sub, reset]);

  const planId = Number(watch('planId'));
  const plan = plans.find((p) => p.id === planId) || sub.plan;
  const assignable = plans.filter((p) => p.status === 'ACTIVE' || p.id === sub.plan.id);

  const onPlanChange = (e) => {
    const p = plans.find((x) => x.id === Number(e.target.value));
    if (!p) return;
    const d = planDefaults(p);
    ['maxHostels', 'maxBedsPerHostel', 'maxManagers', 'customMonthlyPrice'].forEach((k) => setValue(k, d[k], { shouldDirty: true }));
    notify('Limits were reset to the new plan\'s values. Adjust them if this owner needs custom limits.', 'info');
  };

  const submit = async (v) => {
    setServerError(null);
    const check = validateSubscriptionForm({ plan, form: { ...v, trialDays: '0' } });
    ['graceDays', 'maxHostels', 'maxBedsPerHostel', 'maxManagers', 'customMonthlyPrice'].forEach((k) => {
      if (check[k]) setError(k, { type: 'validate', message: check[k] });
    });
    if (['graceDays', 'maxHostels', 'maxBedsPerHostel', 'maxManagers', 'customMonthlyPrice'].some((k) => check[k])) return;
    if (v.status === 'TRIAL' && !v.trialEndsOn) {
      setError('trialEndsOn', { type: 'validate', message: 'A trial needs an end date' });
      return;
    }
    const body = {
      planId: plan.id, status: v.status, startsOn: v.startsOn, trialEndsOn: v.trialEndsOn || null, periodEndsOn: v.periodEndsOn || null,
      graceDays: Number(v.graceDays), reason: v.reason?.trim() || null,
    };
    LIMIT_FIELDS.forEach(({ key, custom }) => { body[custom] = Number(v[key]) !== plan[key] ? Number(v[key]) : null; });
    body.customMonthlyPrice = v.customMonthlyPrice !== '' && Number(v.customMonthlyPrice) !== Number(plan.monthlyPrice) ? Number(v.customMonthlyPrice) : null;
    try {
      await adminApi.updateOwnerSubscription(ownerId, body);
      notify('Subscription updated');
      onSaved();
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not update the subscription.'));
    }
  };

  return (
    <Modal show title="Edit subscription" size="lg" onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="edit-subscription" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Save changes
        </button>
      </>}>
      <form id="edit-subscription" onSubmit={handleSubmit(submit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <div className="row g-3">
          <div className="col-md-6">
            <label htmlFor="f-planId" className="form-label">Plan</label>
            <select id="f-planId" className="form-select" {...register('planId', { onChange: onPlanChange })}>
              {assignable.map((p) => <option key={p.id} value={p.id}>{p.name}{p.status !== 'ACTIVE' ? ` (${p.status.toLowerCase()})` : ''}</option>)}
            </select>
            <div className="form-text">Includes: <EntitlementList plan={plan} features={features} /></div>
          </div>
          <FormField className="col-md-6" label="Status" name="status" as="select" options={STATUS_OPTIONS} register={register} errors={errors} />
          <FormField className="col-md-3" label="Start date" name="startsOn" type="date" register={register} errors={errors} rules={{ required: 'Enter the start date' }} />
          <FormField className="col-md-3" label="Trial ends on" name="trialEndsOn" type="date" register={register} errors={errors} />
          <FormField className="col-md-3" label="Paid until" name="periodEndsOn" type="date" register={register} errors={errors}
            help="Leave empty for no expiry" />
          <FormField className="col-md-3" label="Grace period (days)" name="graceDays" type="number" min="0" max="90" register={register} errors={errors} />
        </div>
        <h3 className="h6 mt-2 mb-2">Limits</h3>
        <LimitFields plan={plan} register={register} watch={watch} setValue={setValue} errors={errors} />
        <FormField className="mt-3" label="Reason for the change (kept in the history)" name="reason" register={register} errors={errors}
          rules={{ maxLength: { value: 300, message: 'Keep it under 300 characters' } }} />
        <p className="small text-muted-hh mb-0">Lowering a limit never deletes anything. The owner keeps all existing hostels, beds and managers and simply cannot add more beyond the limit.</p>
      </form>
    </Modal>
  );
}

function PaymentModal({ ownerId, onClose, onSaved }) {
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { amount: '', paidOn: today(), method: 'UPI', reference: '', notes: '', extendMonths: '1' },
  });
  const submit = async (v) => {
    setServerError(null);
    try {
      await adminApi.recordSubscriptionPayment(ownerId, {
        amount: Number(v.amount), paidOn: v.paidOn, method: v.method, reference: v.reference.trim() || null,
        notes: v.notes.trim() || null, extendMonths: Number(v.extendMonths),
      });
      notify('Subscription payment recorded');
      onSaved();
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not record the payment.'));
    }
  };
  return (
    <Modal show title="Record subscription payment" onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="sub-payment" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Record payment
        </button>
      </>}>
      <form id="sub-payment" onSubmit={handleSubmit(submit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <p className="small text-muted-hh">Only a payment you record here renews the subscription. This is separate from residents' rent.</p>
        <div className="row g-0 gx-3">
          <FormField className="col-6" label="Amount (₹)" name="amount" type="number" step="0.01" min="0.01" register={register} errors={errors}
            rules={{ required: 'Enter the amount', validate: (x) => Number(x) > 0 || 'Amount must be more than zero' }} />
          <FormField className="col-6" label="Paid on" name="paidOn" type="date" register={register} errors={errors}
            rules={{ required: 'Enter the date', validate: (x) => x <= today() || 'The date cannot be in the future' }} />
          <FormField className="col-6" label="Method" name="method" as="select" options={METHODS} register={register} errors={errors} />
          <FormField className="col-6" label="Extend subscription by (months)" name="extendMonths" type="number" min="0" max="36" register={register} errors={errors}
            help="0 records the payment without changing the dates" />
          <FormField className="col-12" label="Reference (UTR / receipt)" name="reference" register={register} errors={errors} />
          <FormField className="col-12" label="Notes" name="notes" register={register} errors={errors} />
        </div>
      </form>
    </Modal>
  );
}

/** Super Admin view of one owner's subscription: plan, dates, limits against usage, history and payments. */
export default function SubscriptionCard({ ownerId }) {
  const q = useApi(() => adminApi.ownerSubscription(ownerId), [ownerId]);
  const plansQ = useApi(() => adminApi.plans(), []);
  const featuresQ = useApi(() => adminApi.planFeatures(), []);
  const [editing, setEditing] = useState(false);
  const [paying, setPaying] = useState(false);
  const s = q.data;

  const rows = useMemo(() => {
    if (!s) return [];
    const eff = s.effectiveLimits;
    const lim = s.planLimits;
    return [
      { key: 'hostels', label: 'Hostels', used: s.usage.hostels, limit: eff.maxHostels, plan: lim.maxHostels },
      ...s.usage.hostelBeds.map((h) => ({ key: `h${h.hostelId}`, label: `Beds · ${h.hostelName}`, used: h.beds, limit: eff.maxBedsPerHostel, plan: lim.maxBedsPerHostel })),
      { key: 'managers', label: 'Manager accounts', used: s.usage.managers, limit: eff.maxManagers, plan: lim.maxManagers },
    ];
  }, [s]);

  if (q.error) return <ErrorState error={q.error} onRetry={q.reload} />;
  if (!s) return <LoadingState label="Loading subscription…" />;
  const refresh = () => { setEditing(false); setPaying(false); q.reload(); };

  return (
    <section className="hh-card mb-3" aria-labelledby="owner-subscription">
      <div className="hh-card-header d-flex flex-wrap gap-2 justify-content-between align-items-center">
        <h2 className="hh-card-title mb-0" id="owner-subscription"><Icon className="bi bi-tags-fill me-2 text-primary-hh" />Subscription</h2>
        <div className="d-flex gap-2">
          <button type="button" className="btn btn-light btn-sm" onClick={() => setPaying(true)}><Icon className="bi bi-cash-coin me-1" />Record payment</button>
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setEditing(true)}><Icon className="bi bi-pencil me-1" />Edit subscription</button>
        </div>
      </div>
      <div className="row g-3 px-3 pb-3">
        <div className="col-lg-5">
          <div className="d-flex align-items-center gap-2 mb-2">
            <span className="fw-bold fs-5">{s.plan.name}</span>
            <StatusBadge map={SUBSCRIPTION_STATE} value={s.accessState} />
            {s.overridesPlan && <span className="badge text-bg-warning">Custom limits</span>}
          </div>
          <dl className="hh-dl mb-2">
            <dt>Status</dt><dd>{label(s.status)}</dd>
            <dt>Billing</dt><dd>{label(s.billingStatus)}</dd>
            <dt>Started</dt><dd>{date(s.startsOn)}</dd>
            {s.trialEndsOn && <><dt>Trial ends</dt><dd>{date(s.trialEndsOn)}</dd></>}
            <dt>Paid until</dt><dd>{s.periodEndsOn ? date(s.periodEndsOn) : 'No expiry'}</dd>
            <dt>Grace ends</dt><dd>{s.graceEndsOn ? `${date(s.graceEndsOn)} (${s.graceDays} days)` : `${s.graceDays} days`}</dd>
            <dt>Monthly price</dt><dd>{s.effectiveMonthlyPrice == null ? 'Custom' : money(s.effectiveMonthlyPrice)}</dd>
          </dl>
          <div className="small">Includes: <EntitlementList plan={s.plan} features={featuresQ.data || []} /></div>
        </div>
        <div className="col-lg-7">
          {s.overLimit.length > 0 && (
            <div className="alert alert-warning py-2 small" role="status">
              <strong>Over the current limits:</strong> {s.overLimit.join('; ')}. Existing records are kept; only new additions are blocked.
            </div>
          )}
          <table className="table table-sm align-middle mb-0">
            <thead><tr><th>Resource</th><th className="text-end">Used</th><th className="text-end">Limit</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.key}>
                  <td>{r.label}</td>
                  <td className={`text-end ${LEVEL_CLASS[usageLevel(r.used, r.limit)]}`}>{r.used}</td>
                  <td className="text-end">{r.limit}{r.limit !== r.plan && <span className="badge text-bg-warning ms-1" title={`Plan value: ${r.plan}`}>custom</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      <div className="row g-3 px-3 pb-3">
        <div className="col-lg-7">
          <h3 className="h6">History</h3>
          {s.events.length === 0 ? <p className="small text-muted-hh">No changes yet.</p> : (
            <ul className="list-unstyled small mb-0" style={{ maxHeight: 220, overflow: 'auto' }}>
              {s.events.map((e) => (
                <li key={e.id} className="py-1 border-bottom">
                  <span className="fw-medium">{label(e.eventType)}</span> — {e.details}
                  <div className="text-muted-hh">{e.actorName || 'System'} · {dateTime(e.createdAt)}</div>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="col-lg-5">
          <h3 className="h6">Subscription payments</h3>
          {s.payments.length === 0 ? <p className="small text-muted-hh">No payments recorded.</p> : (
            <ul className="list-unstyled small mb-0">
              {s.payments.map((p) => (
                <li key={p.id} className="py-1 border-bottom">
                  <span className="fw-medium">{money(p.amount)}</span> · {label(p.method)} · {date(p.paidOn)}
                  {p.extendedTo && <span className="text-muted-hh"> → paid until {date(p.extendedTo)}</span>}
                  {p.reference && <div className="text-muted-hh">Ref {p.reference}</div>}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
      {editing && plansQ.data && <EditModal ownerId={ownerId} sub={s} plans={plansQ.data} features={featuresQ.data || []} onClose={() => setEditing(false)} onSaved={refresh} />}
      {paying && <PaymentModal ownerId={ownerId} onClose={() => setPaying(false)} onSaved={refresh} />}
    </section>
  );
}
