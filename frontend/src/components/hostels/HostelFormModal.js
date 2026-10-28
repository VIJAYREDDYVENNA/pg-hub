import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import { hostelApi, userApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';

export const HOSTEL_TYPES = [
  { value: 'BOYS', label: 'Boys' },
  { value: 'GIRLS', label: 'Girls' },
  { value: 'CO_LIVING', label: 'Co-living' },
  { value: 'OTHER', label: 'Other' },
];

const EMPTY = {
  name: '', hostelType: 'BOYS', address: '', city: '', state: '', pinCode: '',
  contactNumber: '', email: '', managerUserId: '', status: 'ACTIVE',
};

const toForm = (h) => (h ? {
  name: h.name || '', hostelType: h.hostelType || 'BOYS', address: h.address || '', city: h.city || '',
  state: h.state || '', pinCode: h.pinCode || '', contactNumber: h.contactNumber || '', email: h.email || '',
  managerUserId: h.managerUserId ? String(h.managerUserId) : '', status: h.status || 'ACTIVE',
} : EMPTY);

const blank = (v) => (v === undefined || v === null || String(v).trim() === '' ? null : String(v).trim());

/** Create / edit a hostel (hostel = null for create). Calls onSaved(savedHostel). */
export default function HostelFormModal({ show, hostel, onClose, onSaved }) {
  const { can } = useAuth();
  const { notify } = useToast();
  const [managers, setManagers] = useState([]);
  const [image, setImage] = useState(null);
  const [formError, setFormError] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm({ defaultValues: EMPTY });
  const canPickManager = can('STAFF_MANAGE');

  useEffect(() => {
    if (show) { reset(toForm(hostel)); setImage(null); setFormError(null); }
  }, [show, hostel, reset]);

  useEffect(() => {
    if (!show || !canPickManager) return undefined;
    let alive = true;
    userApi.list()
      .then((list) => { if (alive) setManagers((Array.isArray(list) ? list : list?.content || []).filter((u) => u.enabled !== false)); })
      .catch(() => { if (alive) setManagers([]); });
    return () => { alive = false; };
  }, [show, canPickManager]);

  const submit = async (v) => {
    setFormError(null);
    const body = {
      name: v.name.trim(), hostelType: v.hostelType, address: v.address.trim(), city: v.city.trim(), state: v.state.trim(),
      pinCode: v.pinCode.trim(), contactNumber: blank(v.contactNumber), email: blank(v.email),
      managerUserId: v.managerUserId ? Number(v.managerUserId) : (hostel?.managerUserId && !canPickManager ? hostel.managerUserId : null),
      status: v.status,
    };
    try {
      let saved = hostel ? await hostelApi.update(hostel.id, body) : await hostelApi.create(body);
      if (image) {
        try { saved = await hostelApi.uploadImage(saved.id, image); } catch (e) { notify(`Hostel saved, but the photo upload failed: ${errorMessage(e)}`, 'warning'); }
      }
      notify(hostel ? 'Hostel updated.' : `Hostel "${saved.name}" created.`);
      onSaved?.(saved);
    } catch (e) {
      if (!applyServerErrors(e, setError)) setFormError(errorMessage(e));
      else setFormError('Please correct the highlighted fields.');
    }
  };

  const managerOptions = [{ value: '', label: 'No manager assigned' },
    ...managers.map((u) => ({ value: String(u.id), label: `${u.fullName}${u.email ? ` (${u.email})` : ''}` }))];
  if (hostel?.managerUserId && !managers.some((u) => u.id === hostel.managerUserId)) {
    managerOptions.push({ value: String(hostel.managerUserId), label: hostel.managerName || `User #${hostel.managerUserId}` });
  }

  return (
    <Modal show={show} title={hostel ? `Edit ${hostel.name}` : 'Add hostel'} onClose={onClose} size="lg"
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="hostel-form" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{hostel ? 'Save changes' : 'Create hostel'}
        </button>
      </>}>
      <form id="hostel-form" onSubmit={handleSubmit(submit)} noValidate>
        {formError && <div className="alert alert-danger py-2" role="alert">{formError}</div>}
        <div className="row g-0 gx-md-3">
          <div className="col-md-8"><FormField label="Hostel name" name="name" register={register} errors={errors}
            rules={{ required: 'Enter the hostel name', maxLength: { value: 150, message: 'Max 150 characters' } }} /></div>
          <div className="col-md-4"><FormField label="Type" name="hostelType" as="select" options={HOSTEL_TYPES} register={register} errors={errors} rules={{ required: 'Select a type' }} /></div>
          <div className="col-12"><FormField label="Address" name="address" register={register} errors={errors}
            rules={{ required: 'Enter the address', maxLength: { value: 255, message: 'Max 255 characters' } }} /></div>
          <div className="col-md-4"><FormField label="City" name="city" register={register} errors={errors} rules={{ required: 'Enter the city' }} /></div>
          <div className="col-md-4"><FormField label="State" name="state" register={register} errors={errors} rules={{ required: 'Enter the state' }} /></div>
          <div className="col-md-4"><FormField label="PIN code" name="pinCode" inputMode="numeric" register={register} errors={errors}
            rules={{ required: 'Enter the PIN code', pattern: { value: /^[0-9]{6}$/, message: 'PIN code must be 6 digits' } }} /></div>
          <div className="col-md-6"><FormField label="Contact number" name="contactNumber" type="tel" register={register} errors={errors}
            rules={{ pattern: { value: /^$|^[+0-9 ]{7,20}$/, message: 'Enter a valid phone number' } }} /></div>
          <div className="col-md-6"><FormField label="Email" name="email" type="email" register={register} errors={errors} /></div>
          {canPickManager && (
            <div className="col-md-6"><FormField label="Manager" name="managerUserId" as="select" options={managerOptions} register={register} errors={errors}
              help="Staff user responsible for this hostel (optional)." /></div>
          )}
          <div className="col-md-6"><FormField label="Status" name="status" as="select" register={register} errors={errors}
            options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} /></div>
          <div className="col-12 mb-2">
            <label className="form-label" htmlFor="hostel-image">{hostel?.imageUrl ? 'Replace photo' : 'Photo'} (optional)</label>
            <input id="hostel-image" type="file" accept="image/*" className="form-control" onChange={(e) => setImage(e.target.files?.[0] || null)} />
            <div className="form-text">JPG or PNG. Shown on the hostel cards.</div>
          </div>
        </div>
      </form>
    </Modal>
  );
}
