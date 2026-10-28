import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { userApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import HostelAssignPicker from '../../components/managers/HostelAssignPicker';
import { ErrorState, LoadingState } from '../../components/common/States';
import { PASSWORD_RULE } from '../auth/ResetPasswordPage';
import Icon from '../../components/common/Icon';
import { diffAssignments, managersByHostel, updateBody, useManagers } from './managerCommon';

const crumb = (label) => [{ label: 'Dashboard', to: '/' }, { label: 'Managers', to: '/managers' }, { label }];

/** Create a manager (/managers/new) or edit one (/managers/:managerId/edit). */
export default function ManagerFormPage() {
  const { managerId } = useParams();
  const editing = !!managerId;
  const navigate = useNavigate();
  const { hostels } = useHostels();
  const { notify } = useToast();
  const { managers, data, loading, error, reload } = useManagers();
  const manager = useMemo(() => managers.find((m) => String(m.id) === String(managerId)), [managers, managerId]);
  const [hostelIds, setHostelIds] = useState([]);
  const [hostelError, setHostelError] = useState(null);
  const [serverError, setServerError] = useState(null);
  const [showPwd, setShowPwd] = useState(false);
  const [pending, setPending] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { fullName: '', email: '', phone: '', password: '', status: 'ACTIVE' },
  });

  useEffect(() => {
    if (manager) {
      reset({ fullName: manager.fullName, email: manager.email, phone: manager.phone || '', password: '', status: manager.enabled ? 'ACTIVE' : 'INACTIVE' });
      setHostelIds(manager.hostelIds || []);
    }
  }, [manager, reset]);

  const save = async (v) => {
    setServerError(null);
    try {
      let saved;
      if (editing) {
        saved = await userApi.update(manager.id, updateBody(manager, {
          fullName: v.fullName.trim(), phone: v.phone.trim(), hostelIds, enabled: v.status === 'ACTIVE',
        }));
      } else {
        // The role's default permission set is applied by the server when none are sent.
        saved = await userApi.create({
          fullName: v.fullName.trim(), email: v.email.trim(), phone: v.phone.trim(), password: v.password,
          roleCode: 'MANAGER', permissions: null, hostelIds,
        });
        if (v.status !== 'ACTIVE') await userApi.update(saved.id, updateBody(saved, { enabled: false }));
      }
      notify(editing ? 'Manager updated' : `Manager ${saved.fullName} created`);
      navigate(`/managers/${saved.id}`, { replace: true });
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the manager.'));
    } finally {
      setPending(null);
    }
  };

  const onSubmit = (v) => {
    if (hostelIds.length === 0) { setHostelError('Assign at least one hostel.'); return; }
    setHostelError(null);
    const { removed } = editing ? diffAssignments(hostels, manager.hostelIds || [], hostelIds) : { removed: [] };
    const changedHostels = editing && (removed.length > 0 || hostelIds.some((id) => !(manager.hostelIds || []).includes(id)));
    if (changedHostels) setPending(v); else save(v);
  };

  if (editing && error) return <ErrorState error={error} onRetry={reload} />;
  if (editing && !data) return <LoadingState />;
  if (editing && !manager) {
    return <><PageHeader title="Manager not found" icon="bi-person-gear" breadcrumb={crumb('Not found')} />
      <div className="hh-card hh-state py-5"><p className="mb-0">This manager does not exist or is not yours.</p></div></>;
  }
  const diff = pending && editing ? diffAssignments(hostels, manager.hostelIds || [], hostelIds) : { added: [], removed: [] };

  return (
    <>
      <PageHeader title={editing ? `Edit manager · ${manager.fullName}` : 'Create manager'} icon={editing ? 'bi-pencil-square' : 'bi-person-plus'}
        subtitle="A manager only ever sees and operates the hostels assigned here" breadcrumb={crumb(editing ? 'Edit' : 'Create')} />
      <form className="hh-card p-3 p-md-4" style={{ maxWidth: 920 }} onSubmit={handleSubmit(onSubmit, () => { if (hostelIds.length === 0) setHostelError('Assign at least one hostel.'); })} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <div className="row g-0 gx-md-3">
          <FormField className="col-md-6" label="Full name" name="fullName" register={register} errors={errors}
            rules={{ required: 'Enter the manager name', maxLength: { value: 150, message: 'Too long' } }} />
          <FormField className="col-md-6" label="Email address (sign-in ID)" name="email" type="email" autoComplete="off" register={register} errors={errors}
            disabled={editing} rules={editing ? {} : { required: 'Enter the email', pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email' } }} />
          <FormField className="col-md-6" label="Mobile number" name="phone" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ required: 'Enter the mobile number', pattern: { value: /^[+0-9 ]{7,20}$/, message: 'Enter a valid mobile number' } }} />
          <FormField className="col-md-6" label="Account status" name="status" as="select" register={register} errors={errors}
            options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} />
          {!editing && (
            <div className="col-md-6 mb-3">
              <label htmlFor="f-password" className="form-label">Initial password<span className="text-danger ms-1" aria-hidden="true">*</span></label>
              <div className="position-relative">
                <input id="f-password" type={showPwd ? 'text' : 'password'} autoComplete="new-password"
                  className={`form-control pe-5 ${errors.password ? 'is-invalid' : ''}`} {...register('password', { ...PASSWORD_RULE, required: 'Enter an initial password' })} />
                <button type="button" className="btn btn-link position-absolute end-0 top-50 translate-middle-y text-muted-hh" style={{ zIndex: 5 }}
                  onClick={() => setShowPwd((s) => !s)} aria-label={showPwd ? 'Hide password' : 'Show password'}>
                  <Icon className={`bi ${showPwd ? 'bi-eye-slash' : 'bi-eye'}`} />
                </button>
              </div>
              {errors.password ? <div className="invalid-feedback d-block">{errors.password.message}</div>
                : <div className="form-text">At least 8 characters with a letter and a number. Share it securely.</div>}
            </div>
          )}
        </div>
        <hr />
        <HostelAssignPicker hostels={hostels} value={hostelIds} onChange={(ids) => { setHostelIds(ids); setHostelError(null); }}
          managerNames={managersByHostel(managers, manager?.id)} error={hostelError} />
        <div className="d-flex gap-2 justify-content-end mt-4">
          <Link to={editing ? `/managers/${manager.id}` : '/managers'} className="btn btn-light">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{editing ? 'Save changes' : 'Create manager'}
          </button>
        </div>
      </form>
      {pending && (
        <ConfirmDialog show title="Confirm assignment changes" confirmLabel="Save changes" onConfirm={() => save(pending)} onClose={() => setPending(null)}
          message={<>
            <span className="d-block mb-2">Save changes for <strong>{manager.fullName}</strong>?</span>
            {diff.added.length > 0 && <span className="d-block small"><strong>Gains access to:</strong> {diff.added.join(', ')}</span>}
            {diff.removed.length > 0 && <span className="d-block small"><strong>Loses access to:</strong> {diff.removed.join(', ')}. This applies immediately.</span>}
          </>} />
      )}
    </>
  );
}
