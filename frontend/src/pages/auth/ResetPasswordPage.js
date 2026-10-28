import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { AuthShell } from './LoginPage';
import Icon from '../../components/common/Icon';

export const PASSWORD_RULE = {
  required: 'Enter a new password',
  pattern: { value: /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/, message: 'At least 8 characters with a letter and a number' },
};

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [done, setDone] = useState(false);
  const [error, setError] = useState(null);
  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm();
  const onSubmit = async ({ password }) => {
    setError(null);
    try { await authApi.resetPassword(token, password); setDone(true); } catch (e) { setError(errorMessage(e)); }
  };
  return (
    <AuthShell>
      <h2 className="fw-bold mb-3">Choose a new password</h2>
      {!token && <div className="alert alert-warning">This link is missing its reset token. Request a new link.</div>}
      {done ? (
        <div className="alert alert-success">Your password has been reset. <Link to="/login">Sign in</Link></div>
      ) : token && (
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          {error && <div className="alert alert-danger py-2">{error}</div>}
          <label htmlFor="password" className="form-label">New password</label>
          <input id="password" type="password" autoComplete="new-password" className={`form-control mb-1 ${errors.password ? 'is-invalid' : ''}`} {...register('password', PASSWORD_RULE)} />
          {errors.password && <div className="invalid-feedback d-block">{errors.password.message}</div>}
          <label htmlFor="confirm" className="form-label mt-3">Confirm password</label>
          <input id="confirm" type="password" autoComplete="new-password" className={`form-control mb-3 ${errors.confirm ? 'is-invalid' : ''}`}
            {...register('confirm', { validate: (v) => v === watch('password') || 'Passwords do not match' })} />
          {errors.confirm && <div className="invalid-feedback d-block mb-3">{errors.confirm.message}</div>}
          <button className="btn btn-primary btn-lg w-100" disabled={isSubmitting}>Reset password</button>
        </form>
      )}
      <div className="mt-4 text-center"><Link to="/login"><Icon className="bi bi-arrow-left me-1" />Back to sign in</Link></div>
    </AuthShell>
  );
}
