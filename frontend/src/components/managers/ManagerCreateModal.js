import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { userApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import HostelAssignPicker from './HostelAssignPicker';
import { PASSWORD_RULE } from '../../pages/auth/ResetPasswordPage';
import { managersByHostel, updateBody } from '../../pages/managers/managerCommon';
import Icon from '../common/Icon';

const FORM_ID = 'manager-create-form';

function CreateForm({ managers, onClose, onCreated }) {
  const { hostels } = useHostels();
  const { notify } = useToast();
  const [hostelIds, setHostelIds] = useState([]);
  const [hostelError, setHostelError] = useState(null);
  const [serverError, setServerError] = useState(null);
  const [showPwd, setShowPwd] = useState(false);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { fullName: '', email: '', phone: '', password: '', status: 'ACTIVE' },
  });

  const save = async (v) => {
    if (hostelIds.length === 0) { setHostelError('Assign at least one hostel.'); return; }
    setServerError(null);
    try {
      // The role's default permission set is applied by the server when none are sent.
      let saved = await userApi.create({
        fullName: v.fullName.trim(), email: v.email.trim(), phone: v.phone.trim(), password: v.password,
        roleCode: 'MANAGER', permissions: null, hostelIds,
      });
      if (v.status !== 'ACTIVE') saved = await userApi.update(saved.id, updateBody(saved, { enabled: false }));
      notify(`Manager ${saved.fullName} created`);
      onCreated?.(saved);
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the manager.'));
    }
  };

  return (
    <Modal show title="Create manager" size="lg" onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form={FORM_ID} className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Create manager
        </button>
      </>}>
      <p className="small text-muted-hh">A manager only ever sees and operates the hostels assigned here.</p>
      <form id={FORM_ID} onSubmit={handleSubmit(save, () => { if (hostelIds.length === 0) setHostelError('Assign at least one hostel.'); })} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <div className="row g-0 gx-md-3">
          <FormField className="col-md-6" label="Full name" name="fullName" register={register} errors={errors}
            rules={{ required: 'Enter the manager name', maxLength: { value: 150, message: 'Too long' } }} />
          <FormField className="col-md-6" label="Email address (sign-in ID)" name="email" type="email" autoComplete="off" register={register} errors={errors}
            rules={{ required: 'Enter the email', pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email' } }} />
          <FormField className="col-md-6" label="Mobile number" name="phone" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ required: 'Enter the mobile number', pattern: { value: /^[+0-9 ]{7,20}$/, message: 'Enter a valid mobile number' } }} />
          <FormField className="col-md-6" label="Account status" name="status" as="select" register={register} errors={errors}
            options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} />
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
        </div>
        <hr />
        <HostelAssignPicker hostels={hostels} value={hostelIds} onChange={(ids) => { setHostelIds(ids); setHostelError(null); }}
          managerNames={managersByHostel(managers || [])} error={hostelError} />
      </form>
    </Modal>
  );
}

/** Create-manager dialog. Mounts the form only while open so every opening starts blank. */
export default function ManagerCreateModal({ show, ...props }) {
  return show ? <CreateForm {...props} /> : null;
}
