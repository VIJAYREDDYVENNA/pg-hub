import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { bookingApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import BedPicker from '../residents/BedPicker';
import { addDays, today } from '../../utils/format';

const MOBILE = /^[+]?[0-9 ]{10,15}$/;

/**
 * Reserve a bed for a future guest. `prefill` can carry an enquiry ({ enquiryId, hostelId, guestName, guestMobile,
 * expectedAdmissionDate }) so the reservation links back to it.
 */
export default function BookingFormModal({ show, prefill, onClose, onSaved }) {
  const { hostels } = useHostels();
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const [pick, setPick] = useState(null);
  const [bedError, setBedError] = useState(null);
  const { register, handleSubmit, reset, watch, setError, formState: { errors, isSubmitting } } = useForm();
  const hostelId = Number(watch('hostelId')) || null;

  useEffect(() => {
    if (!show) return;
    setServerError(null);
    setPick(null);
    setBedError(null);
    reset({
      hostelId: prefill?.hostelId ? String(prefill.hostelId) : '', guestName: prefill?.guestName || '', guestMobile: prefill?.guestMobile || '',
      expectedAdmissionDate: prefill?.expectedAdmissionDate || addDays(1), holdUntil: '', advanceAmount: '', notes: '',
    });
  }, [show, prefill, reset]);

  // A bed from another hostel must not stay selected when the hostel changes
  useEffect(() => { setPick(null); }, [hostelId]);

  const submit = async (v) => {
    setServerError(null);
    if (!pick) {
      setBedError('Choose a bed to reserve');
      return;
    }
    setBedError(null);
    try {
      const saved = await bookingApi.create({
        bedId: pick.bed.id, enquiryId: prefill?.enquiryId || null, guestName: v.guestName.trim(), guestMobile: v.guestMobile.trim(),
        expectedAdmissionDate: v.expectedAdmissionDate, holdUntil: v.holdUntil || null,
        advanceAmount: v.advanceAmount === '' ? null : Number(v.advanceAmount), notes: v.notes.trim() || null,
      });
      notify(`Bed ${saved.bedLabel} in room ${saved.roomNumber} reserved for ${saved.guestName}`);
      onSaved(saved);
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not reserve the bed.'));
    }
  };

  return (
    <Modal show={show} title="Reserve a bed" size="xl" onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="booking-form" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Reserve bed
        </button>
      </>}>
      <form id="booking-form" onSubmit={handleSubmit(submit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <div className="row g-0 gx-3">
          <FormField className="col-md-6" label="Guest name" name="guestName" register={register} errors={errors}
            rules={{ required: 'Enter the guest name', maxLength: { value: 150, message: 'Too long' } }} />
          <FormField className="col-md-6" label="Guest mobile" name="guestMobile" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ required: 'Enter the mobile number', pattern: { value: MOBILE, message: 'Enter a valid mobile number' } }} />
          <FormField className="col-md-3" label="Expected admission" name="expectedAdmissionDate" type="date" min={today()} register={register} errors={errors}
            rules={{ required: 'Enter the admission date', validate: (x) => x >= today() || 'The date cannot be in the past' }} />
          <FormField className="col-md-3" label="Hold bed until" name="holdUntil" type="date" min={today()} register={register} errors={errors}
            rules={{ validate: (x) => !x || x >= today() || 'The date cannot be in the past' }} help="The reservation lapses after this date" />
          <FormField className="col-md-3" label="Advance received (₹)" name="advanceAmount" type="number" min="0" step="0.01" register={register} errors={errors}
            rules={{ min: { value: 0, message: 'Cannot be negative' } }} help="Recorded on the reservation only" />
          <div className="col-md-3 mb-3">
            <label htmlFor="f-hostelId" className="form-label">Hostel<span className="text-danger ms-1" aria-hidden="true">*</span></label>
            <select id="f-hostelId" className={`form-select ${errors.hostelId ? 'is-invalid' : ''}`} {...register('hostelId', { required: 'Choose a hostel' })}>
              <option value="">Select hostel…</option>
              {hostels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
            </select>
            {errors.hostelId && <div className="invalid-feedback d-block">{errors.hostelId.message}</div>}
          </div>
          <FormField className="col-12" label="Notes" name="notes" register={register} errors={errors}
            rules={{ maxLength: { value: 1000, message: 'Keep it under 1000 characters' } }} />
        </div>
        <h3 className="h6">Bed</h3>
        <p className="small text-muted-hh">Only beds that are free today are listed. The reservation keeps the bed out of other admissions until the hold date passes or it is cancelled.</p>
        {bedError && <div className="alert alert-danger py-2" role="alert">{bedError}</div>}
        {pick && <div className="alert alert-light border py-2 small">Selected: Room {pick.room.roomNumber} · Bed {pick.bed.label}</div>}
        <BedPicker hostelId={hostelId} selectedBedId={pick?.bed.id} onSelect={setPick} />
      </form>
    </Modal>
  );
}
