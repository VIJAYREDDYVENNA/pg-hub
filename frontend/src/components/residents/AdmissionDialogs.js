import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import HostelFilter from '../common/HostelFilter';
import BedPicker from './BedPicker';
import { admissionApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import { addDays, money, today } from '../../utils/format';

const MONEY_RULE = { pattern: { value: /^\d+(\.\d{1,2})?$/, message: 'Enter an amount like 5000 or 5000.50' } };

/** Edit expected checkout / confirmation / rent / notes of an active admission. */
export function EditAdmissionModal({ admission, onClose, onDone }) {
  const { notify } = useToast();
  const [formError, setFormError] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm();

  useEffect(() => {
    if (admission) {
      reset({ expectedCheckoutDate: admission.expectedCheckoutDate || '', checkoutConfirmed: !!admission.checkoutConfirmed,
        monthlyRent: admission.monthlyRent ?? '', notes: admission.notes || '' });
      setFormError(null);
    }
  }, [admission, reset]);

  const submit = async (v) => {
    setFormError(null);
    try {
      await admissionApi.update(admission.id, {
        expectedCheckoutDate: v.expectedCheckoutDate || null,
        checkoutConfirmed: !!v.expectedCheckoutDate && !!v.checkoutConfirmed,
        monthlyRent: String(v.monthlyRent).trim(),
        notes: v.notes?.trim() || null,
      });
      notify('Stay details updated.');
      onDone();
    } catch (e) {
      if (applyServerErrors(e, setError)) setFormError('Please correct the highlighted fields.');
      else setFormError(errorMessage(e));
    }
  };

  return (
    <Modal show={!!admission} title="Edit stay details" onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="admission-edit-form" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Save
        </button>
      </>}>
      <form id="admission-edit-form" onSubmit={handleSubmit(submit)} noValidate>
        {formError && <div className="alert alert-danger py-2">{formError}</div>}
        <FormField label="Expected checkout date" name="expectedCheckoutDate" type="date" min={admission ? addDays(1, admission.admissionDate) : undefined}
          register={register} errors={errors} help="Leave empty if the stay is open-ended." />
        <div className="form-check mb-3">
          <input className="form-check-input" type="checkbox" id="ea-confirmed" {...register('checkoutConfirmed')} />
          <label className="form-check-label" htmlFor="ea-confirmed">Checkout date confirmed by the resident</label>
          <div className="form-text">Unconfirmed dates are shown as "estimated" in the vacancy forecast.</div>
        </div>
        <FormField label="Monthly rent (₹)" name="monthlyRent" inputMode="decimal" register={register} errors={errors}
          rules={{ required: 'Enter the monthly rent', ...MONEY_RULE }} help="Applies to rent invoices generated from now on." />
        <FormField label="Notes" name="notes" as="textarea" register={register} errors={errors}
          rules={{ maxLength: { value: 1000, message: 'Max 1000 characters' } }} />
      </form>
    </Modal>
  );
}

/** Move an admitted resident to another free bed (any accessible hostel). */
export function TransferModal({ admission, onClose, onDone }) {
  const { notify } = useToast();
  const [hostelId, setHostelId] = useState(null);
  const [pick, setPick] = useState(null);
  const [transferDate, setTransferDate] = useState(today());
  const [newMonthlyRent, setRent] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);

  useEffect(() => {
    if (admission) {
      setHostelId(admission.hostelId); setPick(null); setTransferDate(today()); setRent(''); setReason(''); setErr(null);
    }
  }, [admission]);

  const choose = (p) => {
    setPick(p);
    setRent(String(p.bed.rent) !== String(admission.monthlyRent) ? String(p.bed.rent ?? '') : '');
  };

  const submit = async () => {
    if (!pick) { setErr('Choose the new bed.'); return; }
    if (newMonthlyRent && !/^\d+(\.\d{1,2})?$/.test(newMonthlyRent.trim())) { setErr('Enter a valid rent amount.'); return; }
    setBusy(true); setErr(null);
    try {
      await admissionApi.transfer(admission.id, {
        newBedId: pick.bed.id, transferDate, newMonthlyRent: newMonthlyRent.trim() || null, reason: reason.trim() || null,
      });
      notify(`Moved to room ${pick.room.roomNumber}, bed ${pick.bed.label}.`);
      onDone();
    } catch (e) {
      const fe = e?.response?.data?.fieldErrors;
      setErr(fe && Object.keys(fe).length ? Object.values(fe).join(' ') : errorMessage(e));
      if (e?.response?.status === 409) setPick(null);
    } finally { setBusy(false); }
  };

  const cur = admission?.currentBed;
  return (
    <Modal show={!!admission} title="Transfer to another bed" onClose={onClose} size="xl"
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={busy}>Cancel</button>
        <button type="button" className="btn btn-primary" onClick={submit} disabled={busy || !pick}>
          {busy && <span className="spinner-border spinner-border-sm me-2" />}Transfer{pick ? ` to ${pick.room.roomNumber}-${pick.bed.label}` : ''}
        </button>
      </>}>
      {err && <div className="alert alert-danger py-2" role="alert">{err}</div>}
      {cur && <p className="small text-muted-hh">Currently in <strong>{cur.hostelName} · {cur.buildingName} · Room {cur.roomNumber} · Bed {cur.bedLabel}</strong> since {cur.startDate}.</p>}
      <div className="row g-3 mb-3">
        <div className="col-md-4">
          <label className="form-label" htmlFor="tr-hostel">Hostel</label>
          <HostelFilter id="tr-hostel" allowAll={false} value={hostelId} onChange={(id) => { setHostelId(id); setPick(null); }} />
        </div>
        <div className="col-6 col-md-4">
          <label className="form-label" htmlFor="tr-date">Transfer date</label>
          <input id="tr-date" type="date" className="form-control" value={transferDate} max={today()} min={cur?.startDate}
            onChange={(e) => setTransferDate(e.target.value)} />
        </div>
        <div className="col-6 col-md-4">
          <label className="form-label" htmlFor="tr-rent">New monthly rent (₹)</label>
          <input id="tr-rent" className="form-control" inputMode="decimal" value={newMonthlyRent} placeholder={`Keep ${money(admission?.monthlyRent)}`}
            onChange={(e) => setRent(e.target.value)} />
        </div>
        <div className="col-12">
          <label className="form-label" htmlFor="tr-reason">Reason (optional)</label>
          <input id="tr-reason" className="form-control" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Requested a single room" />
        </div>
      </div>
      <h6 className="fw-bold">Choose a free bed</h6>
      <BedPicker hostelId={hostelId} selectedBedId={pick?.bed.id} onSelect={choose} excludeBedId={cur?.bedId} />
    </Modal>
  );
}
