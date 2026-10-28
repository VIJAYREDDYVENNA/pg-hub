import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import ResidentPicker from './ResidentPicker';
import { invoiceApi, paymentApi, residentApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import { date, label, money, today } from '../../utils/format';
import { PAYMENT_METHODS } from '../../utils/status';
import { toPaise } from './financeUtils';
import Icon from '../common/Icon';

const amountRules = {
  required: 'Enter an amount',
  validate: (v) => toPaise(v) >= 1 || 'Amount must be more than zero',
};

function SubmitFooter({ onClose, busy, formId, label: text, variant = 'primary' }) {
  return (
    <>
      <button type="button" className="btn btn-light" onClick={onClose} disabled={busy}>Cancel</button>
      <button type="submit" form={formId} className={`btn btn-${variant}`} disabled={busy}>
        {busy && <span className="spinner-border spinner-border-sm me-2" />}{text}
      </button>
    </>
  );
}

/** Loads the full profile (balance + admissions) for a picked resident. */
function useResidentProfile(residentId) {
  const [state, setState] = useState({ profile: null, loading: false, error: null });
  useEffect(() => {
    if (!residentId) { setState({ profile: null, loading: false, error: null }); return undefined; }
    let alive = true;
    setState({ profile: null, loading: true, error: null });
    residentApi.get(residentId)
      .then((p) => { if (alive) setState({ profile: p, loading: false, error: null }); })
      .catch((e) => { if (alive) setState({ profile: null, loading: false, error: errorMessage(e) }); });
    return () => { alive = false; };
  }, [residentId]);
  return state;
}

/** All admissions of a resident, current first, without duplicates. */
function admissionsOf(profile) {
  if (!profile) return [];
  const list = [profile.currentAdmission, ...(profile.admissionHistory || [])].filter(Boolean);
  const seen = new Set();
  return list.filter((a) => (seen.has(a.id) ? false : seen.add(a.id)));
}

const admissionOption = (a) => ({
  value: String(a.id),
  label: `${a.hostelName || 'Admission'} · from ${date(a.admissionDate)}${a.status ? ` (${label(a.status)})` : ''}`,
});

/** Ad-hoc charge invoice (electricity, laundry…). */
export function ChargeModal({ show, onClose, onDone, hostelId }) {
  const { notify } = useToast();
  const [resident, setResident] = useState(null);
  const [residentError, setResidentError] = useState(null);
  const [error, setError] = useState(null);
  const { register, handleSubmit, reset, setError: setFieldError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { description: '', amount: '', dueDate: today(), notes: '' },
  });

  useEffect(() => {
    if (show) { reset({ description: '', amount: '', dueDate: today(), notes: '' }); setResident(null); setError(null); setResidentError(null); }
  }, [show, reset]);

  const submit = async (v) => {
    if (!resident) { setResidentError('Select the resident to charge'); return; }
    setError(null);
    try {
      const inv = await invoiceApi.create({ residentId: resident.id, description: v.description.trim(), amount: Number(v.amount),
        dueDate: v.dueDate, notes: v.notes?.trim() || null });
      notify(`Charge ${inv.invoiceNumber} of ${money(inv.totalAmount)} added for ${resident.fullName}.`);
      onDone?.(inv);
    } catch (e) {
      if (!applyServerErrors(e, setFieldError)) setError(errorMessage(e));
    }
  };

  return (
    <Modal show={show} title="Add a charge" onClose={onClose}
      footer={<SubmitFooter onClose={onClose} busy={isSubmitting} formId="charge-form" label="Add charge" />}>
      <form id="charge-form" onSubmit={handleSubmit(submit)} noValidate>
        {error && <div className="alert alert-danger py-2">{error}</div>}
        <p className="small text-muted-hh">Creates an invoice for a one-off charge such as electricity, laundry or damages. It is added to the resident's dues; no money is recorded.</p>
        <ResidentPicker id="charge-resident" value={resident} onChange={(r) => { setResident(r); setResidentError(null); }}
          hostelId={hostelId} required error={residentError} autoFocus />
        <FormField label="Description" name="description" register={register} errors={errors}
          rules={{ required: 'Describe the charge', maxLength: { value: 255, message: 'Keep it under 255 characters' } }}
          placeholder="e.g. Electricity – September" />
        <div className="row g-2">
          <div className="col-sm-6">
            <FormField label="Amount (₹)" name="amount" type="number" inputMode="decimal" step="0.01" min="0.01"
              register={register} errors={errors} rules={amountRules} />
          </div>
          <div className="col-sm-6">
            <FormField label="Due date" name="dueDate" type="date" register={register} errors={errors} rules={{ required: 'Choose a due date' }} />
          </div>
        </div>
        <FormField label="Notes (optional)" name="notes" as="textarea" register={register} errors={errors}
          rules={{ maxLength: { value: 500, message: 'Keep notes under 500 characters' } }} />
      </form>
    </Modal>
  );
}

/** Records money paid back to a resident: a security deposit or unused advance credit. */
export function RefundModal({ show, onClose, onDone, hostelId }) {
  const { notify } = useToast();
  const [resident, setResident] = useState(null);
  const [residentError, setResidentError] = useState(null);
  const [error, setError] = useState(null);
  const { profile, loading, error: profileError } = useResidentProfile(resident?.id);
  const blank = { refundType: 'DEPOSIT', admissionId: '', amount: '', refundDate: today(), method: 'UPI', transactionReference: '', reason: '' };
  const { register, handleSubmit, reset, watch, setValue, setError: setFieldError, formState: { errors, isSubmitting } } = useForm({ defaultValues: blank });
  const refundType = watch('refundType');
  const admissions = useMemo(() => admissionsOf(profile), [profile]);

  useEffect(() => {
    if (show) { reset(blank); setResident(null); setError(null); setResidentError(null); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [show]);

  useEffect(() => {
    if (admissions.length) setValue('admissionId', String(admissions[0].id));
  }, [admissions, setValue]);

  const submit = async (v) => {
    if (!resident) { setResidentError('Select the resident being refunded'); return; }
    if (v.refundType === 'DEPOSIT' && !v.admissionId) { setFieldError('admissionId', { message: 'Select the admission whose deposit is refunded' }); return; }
    setError(null);
    try {
      await paymentApi.refund({
        residentId: resident.id,
        refundType: v.refundType,
        admissionId: v.refundType === 'DEPOSIT' ? Number(v.admissionId) : (v.admissionId ? Number(v.admissionId) : null),
        amount: Number(v.amount),
        refundDate: v.refundDate,
        method: v.method,
        transactionReference: v.transactionReference?.trim() || null,
        reason: v.reason.trim(),
      });
      notify(`Refund of ${money(v.amount)} to ${resident.fullName} recorded.`);
      onDone?.();
    } catch (e) {
      if (!applyServerErrors(e, setFieldError)) setError(errorMessage(e));
    }
  };

  const available = refundType === 'DEPOSIT' ? profile?.balance?.depositHeld : profile?.balance?.credit;

  return (
    <Modal show={show} title="Record a refund" onClose={onClose}
      footer={<SubmitFooter onClose={onClose} busy={isSubmitting} formId="refund-form" label="Record refund" />}>
      <form id="refund-form" onSubmit={handleSubmit(submit)} noValidate>
        {error && <div className="alert alert-danger py-2">{error}</div>}
        <p className="small text-muted-hh">Record a refund only after the money has actually been paid back to the resident.</p>
        <ResidentPicker id="refund-resident" value={resident} onChange={(r) => { setResident(r); setResidentError(null); }}
          hostelId={hostelId} required error={residentError} autoFocus />
        {loading && <div className="small text-muted-hh mb-3"><span className="spinner-border spinner-border-sm me-1" />Loading balance…</div>}
        {profileError && <div className="alert alert-warning py-2 small">{profileError}</div>}
        <fieldset className="mb-3">
          <legend className="form-label fs-6">What is being refunded?</legend>
          <div className="d-flex gap-2 flex-wrap">
            {[{ v: 'DEPOSIT', l: 'Security deposit', i: 'bi-safe' }, { v: 'ADVANCE', l: 'Advance / extra paid', i: 'bi-wallet2' }].map((o) => (
              <label key={o.v} className={`btn ${refundType === o.v ? 'btn-primary' : 'btn-light'} flex-fill py-2`}>
                <input type="radio" className="visually-hidden" value={o.v} {...register('refundType')} />
                <Icon className={`bi ${o.i} me-1`} />{o.l}
              </label>
            ))}
          </div>
        </fieldset>
        {profile && (
          <div className="alert alert-light border py-2 small">
            {refundType === 'DEPOSIT' ? 'Deposit currently held' : 'Advance credit available'}: <strong>{money(available)}</strong>
          </div>
        )}
        {refundType === 'DEPOSIT' && profile && (
          admissions.length ? (
            <FormField label="Admission" name="admissionId" as="select" register={register} errors={errors}
              rules={{ required: 'Select the admission' }} options={admissions.map(admissionOption)} />
          ) : <div className="alert alert-warning py-2 small">This resident has no admission with a deposit.</div>
        )}
        <div className="row g-2">
          <div className="col-sm-6">
            <FormField label="Amount (₹)" name="amount" type="number" inputMode="decimal" step="0.01" min="0.01"
              register={register} errors={errors} rules={amountRules} />
          </div>
          <div className="col-sm-6">
            <FormField label="Refund date" name="refundDate" type="date" max={today()} register={register} errors={errors}
              rules={{ required: 'Choose the date', validate: (v) => v <= today() || 'Refund date cannot be in the future' }} />
          </div>
          <div className="col-sm-6">
            <FormField label="Paid back by" name="method" as="select" register={register} errors={errors}
              rules={{ required: true }} options={PAYMENT_METHODS} />
          </div>
          <div className="col-sm-6">
            <FormField label="Reference (optional)" name="transactionReference" register={register} errors={errors}
              rules={{ maxLength: { value: 100, message: 'Too long' } }} placeholder="UTR / UPI ref" />
          </div>
        </div>
        <FormField label="Reason" name="reason" as="textarea" register={register} errors={errors}
          rules={{ required: 'Enter the reason', maxLength: { value: 500, message: 'Keep it under 500 characters' } }}
          placeholder="e.g. Deposit returned at checkout after room inspection" />
      </form>
    </Modal>
  );
}

/** Settles outstanding dues from the security deposit held (no cash changes hands). */
export function DepositAdjustModal({ show, onClose, onDone, hostelId }) {
  const { notify } = useToast();
  const [resident, setResident] = useState(null);
  const [residentError, setResidentError] = useState(null);
  const [error, setError] = useState(null);
  const { profile, loading, error: profileError } = useResidentProfile(resident?.id);
  const admissions = useMemo(() => admissionsOf(profile), [profile]);
  const blank = { admissionId: '', amount: '', notes: '' };
  const { register, handleSubmit, reset, setValue, setError: setFieldError, formState: { errors, isSubmitting } } = useForm({ defaultValues: blank });

  useEffect(() => {
    if (show) { reset(blank); setResident(null); setError(null); setResidentError(null); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [show]);

  useEffect(() => {
    if (!profile) return;
    if (admissions.length) setValue('admissionId', String(admissions[0].id));
    const held = toPaise(profile.balance?.depositHeld);
    const due = toPaise(profile.balance?.outstanding);
    const suggestion = Math.min(held || 0, due || 0);
    setValue('amount', suggestion > 0 ? (suggestion / 100).toFixed(2) : '');
  }, [profile, admissions, setValue]);

  const submit = async (v) => {
    if (!resident) { setResidentError('Select the resident'); return; }
    setError(null);
    try {
      const p = await paymentApi.adjustDeposit({ admissionId: Number(v.admissionId), amount: Number(v.amount), notes: v.notes?.trim() || null });
      notify(`${money(p?.amount ?? v.amount)} of ${resident.fullName}'s deposit applied to dues.`);
      onDone?.(p);
    } catch (e) {
      if (!applyServerErrors(e, setFieldError)) setError(errorMessage(e));
    }
  };

  return (
    <Modal show={show} title="Adjust deposit against dues" onClose={onClose}
      footer={<SubmitFooter onClose={onClose} busy={isSubmitting} formId="deposit-form" label="Apply deposit" />}>
      <form id="deposit-form" onSubmit={handleSubmit(submit)} noValidate>
        {error && <div className="alert alert-danger py-2">{error}</div>}
        <p className="small text-muted-hh">Uses part of the security deposit you are holding to clear the resident's unpaid rent and charges (oldest first). No new money is received.</p>
        <ResidentPicker id="deposit-resident" value={resident} onChange={(r) => { setResident(r); setResidentError(null); }}
          hostelId={hostelId} required error={residentError} autoFocus />
        {loading && <div className="small text-muted-hh mb-3"><span className="spinner-border spinner-border-sm me-1" />Loading balance…</div>}
        {profileError && <div className="alert alert-warning py-2 small">{profileError}</div>}
        {profile && (
          <dl className="hh-kv mb-3">
            <dt>Deposit held</dt><dd className="fw-semibold">{money(profile.balance?.depositHeld)}</dd>
            <dt>Outstanding dues</dt><dd className="fw-semibold">{money(profile.balance?.outstanding)}</dd>
          </dl>
        )}
        {profile && (admissions.length ? (
          <FormField label="Admission" name="admissionId" as="select" register={register} errors={errors}
            rules={{ required: 'Select the admission' }} options={admissions.map(admissionOption)} />
        ) : <div className="alert alert-warning py-2 small">This resident has no admission with a deposit.</div>)}
        <FormField label="Amount to apply (₹)" name="amount" type="number" inputMode="decimal" step="0.01" min="0.01"
          register={register} errors={errors} rules={amountRules}
          help="Cannot exceed the deposit held or the outstanding dues." />
        <FormField label="Notes (optional)" name="notes" as="textarea" register={register} errors={errors}
          rules={{ maxLength: { value: 500, message: 'Keep notes under 500 characters' } }} />
      </form>
    </Modal>
  );
}
