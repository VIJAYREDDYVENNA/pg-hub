import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import PageHeader from '../../components/common/PageHeader';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import { ErrorState, LoadingState } from '../../components/common/States';
import { GENDERS, ID_TYPES } from '../../components/residents/ResidentBits';
import { useAuth } from '../../context/AuthContext';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import { residentApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { today } from '../../utils/format';
import Icon from '../../components/common/Icon';

const PHONE = { value: /^[+]?[0-9 ]{10,15}$/, message: 'Enter a valid 10-digit mobile number' };
const OPT_PHONE = { value: /^$|^[+]?[0-9 ]{10,15}$/, message: 'Enter a valid phone number' };
const EMAIL = { value: /^$|^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: 'Enter a valid email address' };

const FIELDS = ['fullName', 'mobile', 'alternateMobile', 'email', 'dateOfBirth', 'gender', 'address', 'emergencyContactName',
  'emergencyContactPhone', 'idDocumentType', 'idDocumentNumber', 'guardianName', 'guardianPhone', 'guardianRelation', 'occupation', 'notes'];

const emptyForm = (hostelId) => ({
  ...Object.fromEntries(FIELDS.map((f) => [f, ''])), hostelId: hostelId ? String(hostelId) : '', whatsappOptIn: false,
});

const fromProfile = (p) => ({
  ...Object.fromEntries(FIELDS.map((f) => [f, p[f] ?? ''])), hostelId: String(p.hostelId), whatsappOptIn: !!p.whatsappOptIn,
});

/** Converts form values to a ResidentRequest (blank optional fields become null). */
export const toResidentRequest = (v) => {
  const body = { hostelId: Number(v.hostelId), whatsappOptIn: !!v.whatsappOptIn };
  FIELDS.forEach((f) => {
    const val = typeof v[f] === 'string' ? v[f].trim() : v[f];
    body[f] = val === '' || val === undefined ? null : val;
  });
  return body;
};

function Section({ title, icon, children }) {
  return (
    <section className="hh-card mb-3">
      <h2 className="hh-card-title mb-3"><Icon className={`bi ${icon} me-2 text-primary-hh`} aria-hidden="true" />{title}</h2>
      <div className="row g-0 gx-md-3">{children}</div>
    </section>
  );
}

export default function ResidentFormPage() {
  const { id } = useParams();
  const isEdit = !!id;
  const navigate = useNavigate();
  const { can } = useAuth();
  const { hostels, selectedHostelId } = useHostels();
  const { notify } = useToast();
  const [loading, setLoading] = useState(isEdit);
  const [loadError, setLoadError] = useState(null);
  const [profile, setProfile] = useState(null);
  const [formError, setFormError] = useState(null);
  const [created, setCreated] = useState(null);
  const { register, handleSubmit, reset, setError, setValue, getValues, formState: { errors, isSubmitting } } = useForm({
    defaultValues: emptyForm(selectedHostelId),
  });

  useEffect(() => { document.title = `${isEdit ? 'Edit resident' : 'Add resident'} · HostelHub`; }, [isEdit]);

  const load = async () => {
    setLoading(true); setLoadError(null);
    try {
      const p = await residentApi.get(id);
      setProfile(p);
      reset(fromProfile(p));
    } catch (e) { setLoadError(e); } finally { setLoading(false); }
  };
  useEffect(() => { if (isEdit) load(); }, [id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Default the hostel once the list is available (create mode).
  useEffect(() => {
    if (!isEdit && !getValues('hostelId') && hostels.length > 0) setValue('hostelId', String(selectedHostelId || hostels[0].id));
  }, [hostels, selectedHostelId, isEdit, getValues, setValue]);

  const submit = async (v) => {
    setFormError(null);
    try {
      const body = toResidentRequest(v);
      // A disabled select is not submitted: keep the current hostel for admitted residents.
      if (admitted) body.hostelId = profile.hostelId;
      if (isEdit) {
        await residentApi.update(id, body);
        notify('Resident details saved.');
        navigate(`/residents/${id}`);
      } else {
        const res = await residentApi.create(body);
        notify(`${res.fullName || body.fullName} added.`);
        setCreated(res);
        window.scrollTo(0, 0);
      }
    } catch (e) {
      if (applyServerErrors(e, setError)) setFormError('Please correct the highlighted fields.');
      else setFormError(errorMessage(e));
      window.scrollTo(0, 0);
    }
  };

  const admitted = isEdit && profile?.status === 'ACTIVE';
  // Keep values stored by older records selectable even if they are not in the standard lists.
  const withCurrent = (opts, value) => (value && !opts.some((o) => o.value === value) ? [...opts, { value, label: value }] : opts);

  if (loading) return <LoadingState label="Loading resident…" />;
  if (loadError) return <ErrorState error={loadError} onRetry={load} />;

  if (created) {
    return (
      <>
        <PageHeader title="Resident added" icon="bi-person-check" />
        <div className="hh-card text-center py-5">
          <Icon className="bi bi-check-circle-fill text-success fs-1" aria-hidden="true" />
          <h2 className="h5 fw-bold mt-2">{created.fullName} is registered</h2>
          <p className="text-muted-hh">They are not in a bed yet. Admit them now, or open the profile to upload documents first.</p>
          <div className="d-flex flex-column flex-sm-row justify-content-center gap-2">
            <Link to={`/residents/${created.id}`} className="btn btn-light"><Icon className="bi bi-person-vcard me-1" />Go to profile</Link>
            <button type="button" className="btn btn-light" onClick={() => { setCreated(null); reset(emptyForm(getValues('hostelId'))); }}>
              <Icon className="bi bi-plus-lg me-1" />Add another</button>
          </div>
        </div>
      </>
    );
  }

  const hostelOptions = [{ value: '', label: 'Select hostel…' }, ...hostels.map((h) => ({ value: String(h.id), label: h.name }))];

  return (
    <>
      <PageHeader title={isEdit ? `Edit ${profile?.fullName || 'resident'}` : 'Add resident'} icon={isEdit ? 'bi-pencil-square' : 'bi-person-plus'}
        subtitle={isEdit ? 'Update personal and contact details' : 'Register a resident. You can admit them to a bed afterwards.'}
        actions={<Link to={isEdit ? `/residents/${id}` : '/residents'} className="btn btn-light"><Icon className="bi bi-arrow-left me-1" />Back</Link>} />

      <form onSubmit={handleSubmit(submit)} noValidate style={{ maxWidth: 960 }}>
        {formError && <div className="alert alert-danger" role="alert">{formError}</div>}

        <Section title="Personal details" icon="bi-person">
          <div className="col-md-8"><FormField label="Full name" name="fullName" register={register} errors={errors} autoComplete="name"
            rules={{ required: 'Enter the full name', maxLength: { value: 150, message: 'Max 150 characters' } }} /></div>
          <div className="col-md-4"><FormField label="Hostel" name="hostelId" as="select" options={hostelOptions} register={register} errors={errors}
            disabled={admitted} help={admitted ? 'Use a bed transfer to move an admitted resident.' : undefined}
            rules={{ required: 'Select a hostel' }} /></div>
          <div className="col-6 col-md-4"><FormField label="Date of birth" name="dateOfBirth" type="date" max={today()} register={register} errors={errors} /></div>
          <div className="col-6 col-md-4"><FormField label="Gender" name="gender" as="select" options={withCurrent(GENDERS, profile?.gender)} register={register} errors={errors} /></div>
          <div className="col-md-4"><FormField label="Occupation" name="occupation" register={register} errors={errors} placeholder="Student, IT professional…"
            rules={{ maxLength: { value: 100, message: 'Max 100 characters' } }} /></div>
        </Section>

        <Section title="Contact" icon="bi-telephone">
          <div className="col-md-6"><FormField label="Mobile" name="mobile" type="tel" inputMode="tel" autoComplete="tel" register={register} errors={errors}
            rules={{ required: 'Enter the mobile number', pattern: PHONE }} /></div>
          <div className="col-md-6"><FormField label="Alternate mobile" name="alternateMobile" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ pattern: OPT_PHONE }} /></div>
          <div className="col-md-6"><FormField label="Email" name="email" type="email" autoComplete="email" register={register} errors={errors}
            rules={{ pattern: EMAIL }} /></div>
          <div className="col-12"><FormField label="Permanent address" name="address" as="textarea" register={register} errors={errors}
            rules={{ maxLength: { value: 500, message: 'Max 500 characters' } }} /></div>
        </Section>

        <Section title="Emergency contact & guardian" icon="bi-shield-plus">
          <div className="col-md-6"><FormField label="Emergency contact name" name="emergencyContactName" register={register} errors={errors} /></div>
          <div className="col-md-6"><FormField label="Emergency contact phone" name="emergencyContactPhone" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ pattern: OPT_PHONE }} /></div>
          <div className="col-md-4"><FormField label="Guardian name" name="guardianName" register={register} errors={errors} /></div>
          <div className="col-md-4"><FormField label="Guardian phone" name="guardianPhone" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ pattern: OPT_PHONE }} /></div>
          <div className="col-md-4"><FormField label="Relation" name="guardianRelation" register={register} errors={errors} placeholder="Father, Mother…" /></div>
        </Section>

        <Section title="ID document" icon="bi-person-vcard">
          <div className="col-md-6"><FormField label="Document type" name="idDocumentType" as="select" options={withCurrent(ID_TYPES, profile?.idDocumentType)} register={register} errors={errors} /></div>
          <div className="col-md-6"><FormField label="Document number" name="idDocumentNumber" register={register} errors={errors}
            rules={{ maxLength: { value: 60, message: 'Max 60 characters' } }} help="Upload scans from the resident's profile after saving." /></div>
        </Section>

        <section className="hh-card mb-3">
          <h2 className="hh-card-title mb-3"><Icon className="bi bi-whatsapp me-2 text-success" aria-hidden="true" />WhatsApp messages</h2>
          <div className="form-check">
            <input className="form-check-input" type="checkbox" id="f-whatsappOptIn" {...register('whatsappOptIn')} />
            <label className="form-check-label" htmlFor="f-whatsappOptIn">
              The resident agrees to receive WhatsApp messages from the hostel (admission confirmation, rent reminders, receipts and announcements).
            </label>
          </div>
          <div className="form-text">Only tick this if the resident has given consent. They can opt out at any time; messages are never sent without consent.</div>
          <div className="mt-3">
            <FormField label="Notes" name="notes" as="textarea" register={register} errors={errors}
              rules={{ maxLength: { value: 1000, message: 'Max 1000 characters' } }} />
          </div>
        </section>

        <div className="d-flex flex-column flex-sm-row gap-2 justify-content-end mb-4">
          <Link to={isEdit ? `/residents/${id}` : '/residents'} className="btn btn-light">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{isEdit ? 'Save changes' : 'Add resident'}
          </button>
        </div>
      </form>
    </>
  );
}
