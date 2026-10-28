import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { userApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import { PASSWORD_RULE } from '../../pages/auth/ResetPasswordPage';

/** Owner sets a new password for one of their logins; the user is signed out everywhere. */
export default function ResetPasswordModal({ user, onClose }) {
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, reset, watch, setError, formState: { errors, isSubmitting } } = useForm();
  useEffect(() => { if (user) { reset({ newPassword: '', confirm: '' }); setServerError(null); } }, [user, reset]);
  const onSubmit = async (v) => {
    try {
      const r = await userApi.resetPassword(user.id, v.newPassword);
      notify(r?.message || 'Password reset');
      onClose();
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e));
    }
  };
  return (
    <Modal show={!!user} title={`Reset password · ${user?.fullName || ''}`} onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="reset-pw-form" className="btn btn-danger" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Reset password
        </button>
      </>}>
      <form id="reset-pw-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError && <div className="alert alert-danger py-2">{serverError}</div>}
        <p className="small text-muted-hh">The user is signed out of all devices and must sign in with the new password.</p>
        <FormField label="New password" name="newPassword" type="password" autoComplete="new-password" register={register} errors={errors} rules={PASSWORD_RULE} />
        <FormField label="Confirm password" name="confirm" type="password" autoComplete="new-password" register={register} errors={errors}
          rules={{ validate: (val) => val === watch('newPassword') || 'Passwords do not match' }} />
      </form>
    </Modal>
  );
}
