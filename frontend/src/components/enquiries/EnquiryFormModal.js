import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { enquiryApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import { ENQUIRY_STATUS } from '../../utils/status';
import { GENDERS } from '../residents/ResidentBits';
import { today } from '../../utils/format';

export const SOURCES = ['Walk-in', 'Phone call', 'Website', 'Referral', 'Social media', 'Broker', 'Other'];
const MOBILE = /^[+]?[0-9 ]{10,15}$/;

const empty = {
  fullName: '', mobile: '', email: '', gender: '', hostelId: '', preferredRoomType: '', expectedAdmissionDate: '', budget: '',
  source: '', status: 'NEW', nextFollowUpDate: '', notes: '', whatsappOptIn: false,
};

const toForm = (e) => ({
  fullName: e.fullName, mobile: e.mobile, email: e.email || '', gender: e.gender || '', hostelId: e.hostelId ? String(e.hostelId) : '',
  preferredRoomType: e.preferredRoomType || '', expectedAdmissionDate: e.expectedAdmissionDate || '', budget: e.budget ?? '',
  source: e.source || '', status: e.status, nextFollowUpDate: e.nextFollowUpDate || '', notes: e.notes || '', whatsappOptIn: !!e.whatsappOptIn,
});

/** Create or edit an enquiry (a prospective resident). `enquiry` null = new. */
export default function EnquiryFormModal({ show, enquiry, defaultHostelId, onClose, onSaved }) {
  const { hostels } = useHostels();
  const { notify } = useToast();
  const editing = !!enquiry;
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm({ defaultValues: empty });

  useEffect(() => {
    if (!show) return;
    setServerError(null);
    reset(enquiry ? toForm(enquiry) : { ...empty, hostelId: defaultHostelId ? String(defaultHostelId) : '' });
  }, [show, enquiry, defaultHostelId, reset]);

  const submit = async (v) => {
    setServerError(null);
    const body = {
      hostelId: v.hostelId ? Number(v.hostelId) : null, fullName: v.fullName.trim(), mobile: v.mobile.trim(), email: v.email.trim() || null,
      gender: v.gender || null, preferredRoomType: v.preferredRoomType.trim() || null, expectedAdmissionDate: v.expectedAdmissionDate || null,
      budget: v.budget === '' ? null : Number(v.budget), source: v.source || null, status: editing ? v.status : 'NEW',
      nextFollowUpDate: v.nextFollowUpDate || null, notes: v.notes.trim() || null, whatsappOptIn: !!v.whatsappOptIn,
    };
    try {
      const saved = editing ? await enquiryApi.update(enquiry.id, body) : await enquiryApi.create(body);
      notify(editing ? 'Enquiry updated' : `Enquiry added for ${saved.fullName}`);
      onSaved(saved);
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the enquiry.'));
    }
  };

  return (
    <Modal show={show} title={editing ? `Enquiry · ${enquiry.fullName}` : 'New enquiry'} size="lg" onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="enquiry-form" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{editing ? 'Save changes' : 'Add enquiry'}
        </button>
      </>}>
      <form id="enquiry-form" onSubmit={handleSubmit(submit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <div className="row g-0 gx-3">
          <FormField className="col-md-6" label="Name" name="fullName" register={register} errors={errors}
            rules={{ required: 'Enter the name', maxLength: { value: 150, message: 'Too long' } }} />
          <FormField className="col-md-6" label="Mobile" name="mobile" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ required: 'Enter the mobile number', pattern: { value: MOBILE, message: 'Enter a valid mobile number' } }} />
          <FormField className="col-md-6" label="Email" name="email" type="email" register={register} errors={errors}
            rules={{ pattern: { value: /^$|^\S+@\S+\.\S+$/, message: 'Enter a valid email' } }} />
          <FormField className="col-md-6" label="Gender" name="gender" as="select" options={GENDERS} register={register} errors={errors} />
          <div className="col-md-6 mb-3">
            <label htmlFor="f-hostelId" className="form-label">Interested in hostel</label>
            <select id="f-hostelId" className="form-select" {...register('hostelId')}>
              <option value="">Any / not decided</option>
              {hostels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
            </select>
          </div>
          <FormField className="col-md-6" label="Preferred room type" name="preferredRoomType" register={register} errors={errors}
            rules={{ maxLength: { value: 30, message: 'Too long' } }} />
          <FormField className="col-md-4" label="Expected admission" name="expectedAdmissionDate" type="date" register={register} errors={errors} />
          <FormField className="col-md-4" label="Budget (₹ / month)" name="budget" type="number" min="0" step="1" register={register} errors={errors}
            rules={{ min: { value: 0, message: 'Cannot be negative' } }} />
          <FormField className="col-md-4" label="Next follow-up" name="nextFollowUpDate" type="date" min={today()} register={register} errors={errors}
            help="You get a reminder on this day" />
          <FormField className="col-md-6" label="Source" name="source" as="select" register={register} errors={errors}
            options={[{ value: '', label: 'Not recorded' }, ...SOURCES.map((s) => ({ value: s, label: s }))]} />
          {editing && (
            <FormField className="col-md-6" label="Status" name="status" as="select" register={register} errors={errors}
              options={Object.entries(ENQUIRY_STATUS).map(([value, m]) => ({ value, label: m.label }))} />
          )}
          <FormField className="col-12" label="Notes" name="notes" as="textarea" register={register} errors={errors}
            rules={{ maxLength: { value: 1000, message: 'Keep it under 1000 characters' } }} />
        </div>
        <div className="form-check">
          <input id="f-whatsappOptIn" type="checkbox" className="form-check-input" {...register('whatsappOptIn')} />
          <label htmlFor="f-whatsappOptIn" className="form-check-label">This person agreed to receive WhatsApp messages</label>
        </div>
      </form>
    </Modal>
  );
}
