import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { authApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { AuthShell } from './LoginPage';
import Icon from '../../components/common/Icon';

export default function ForgotPasswordPage() {
  const [done, setDone] = useState(null);
  const [error, setError] = useState(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm();
  const onSubmit = async ({ email }) => {
    setError(null);
    try { setDone((await authApi.forgotPassword(email.trim())).message); } catch (e) { setError(errorMessage(e)); }
  };
  return (
    <AuthShell>
      <h2 className="fw-bold mb-1">Forgot password</h2>
      <p className="text-muted-hh mb-4">Enter your account email and we'll issue a reset link.</p>
      {done ? (
        <div className="alert alert-success">
          {done}
          {process.env.NODE_ENV !== 'production' && <div className="small mt-2">Development mode: e-mail is not configured, the reset link is written to the backend log.</div>}
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          {error && <div className="alert alert-danger py-2">{error}</div>}
          <label htmlFor="email" className="form-label">Email</label>
          <input id="email" type="email" className={`form-control form-control-lg mb-3 ${errors.email ? 'is-invalid' : ''}`}
            {...register('email', { required: 'Enter your email', pattern: { value: /\S+@\S+\.\S+/, message: 'Enter a valid email' } })} />
          {errors.email && <div className="invalid-feedback d-block mb-3">{errors.email.message}</div>}
          <button className="btn btn-primary btn-lg w-100" disabled={isSubmitting}>Send reset link</button>
        </form>
      )}
      <div className="mt-4 text-center"><Link to="/login"><Icon className="bi bi-arrow-left me-1" />Back to sign in</Link></div>
    </AuthShell>
  );
}
