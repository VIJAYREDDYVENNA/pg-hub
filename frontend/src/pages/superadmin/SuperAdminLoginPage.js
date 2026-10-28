import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { errorMessage } from '../../services/apiClient';
import { AuthShell } from '../auth/LoginPage';
import Icon from '../../components/common/Icon';

const DEV_ACCOUNTS = [
  { label: 'Super Admin', email: 'superadmin@hostelhub.dev', password: 'Admin@12345' },
  { label: 'Owner', email: 'owner@hostelhub.dev', password: 'Owner@123', tenant: true },
  { label: 'Manager', email: 'manager@hostelhub.dev', password: 'Manager@123', tenant: true },
];

/** Dedicated platform-administrator sign-in. Owners and managers cannot sign in here (and vice versa). */
export default function SuperAdminLoginPage() {
  const { user, login, expired, isSuperAdmin } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState(null);
  const [show, setShow] = useState(false);
  const { register, handleSubmit, setValue, formState: { errors, isSubmitting } } = useForm({ defaultValues: { email: '', password: '' } });

  // Arriving from the standard login's development shortcut pre-fills the form.
  useEffect(() => {
    const pre = location.state?.prefill;
    if (pre) { setValue('email', pre.email); setValue('password', pre.password); }
  }, [location.state, setValue]);

  if (user && isSuperAdmin) return <Navigate to="/super-admin/dashboard" replace />;

  const onSubmit = async ({ email, password }) => {
    setError(null);
    try {
      await login(email.trim(), password, 'super-admin');
      navigate('/super-admin/dashboard', { replace: true });
    } catch (e) {
      setError(errorMessage(e, 'Unable to sign in'));
    }
  };

  return (
    <AuthShell>
      <span className="hh-auth-chip mb-3"><Icon className="bi bi-shield-lock-fill" />Platform administration</span>
      <h2 className="fw-bold mb-1" style={{ fontSize: '1.65rem' }}>Super Admin sign in</h2>
      <p className="text-muted-hh mb-4 small">Manage owner accounts and monitor the whole platform.</p>
      {user && !isSuperAdmin && (
        <div className="alert alert-info py-2 small">You are signed in as {user.fullName}. Signing in here replaces that session.</div>
      )}
      {expired && !error && <div className="alert alert-warning py-2 small"><Icon className="bi bi-clock-history me-2" />Your session expired. Please sign in again.</div>}
      {error && <div className="alert alert-danger py-2 small" role="alert"><Icon className="bi bi-exclamation-circle me-2" />{error}</div>}
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="mb-3">
          <label htmlFor="sa-email" className="form-label">Email address</label>
          <div className="input-icon">
            <Icon className="bi bi-envelope" />
            <input id="sa-email" type="email" autoComplete="username" placeholder="admin@example.com"
              className={`form-control form-control-lg ${errors.email ? 'is-invalid' : ''}`}
              {...register('email', { required: 'Enter your email', pattern: { value: /\S+@\S+\.\S+/, message: 'Enter a valid email' } })} />
          </div>
          {errors.email && <div className="invalid-feedback d-block">{errors.email.message}</div>}
        </div>
        <div className="mb-2">
          <label htmlFor="sa-password" className="form-label">Password</label>
          <div className="input-icon position-relative">
            <Icon className="bi bi-lock" />
            <input id="sa-password" type={show ? 'text' : 'password'} autoComplete="current-password" placeholder="••••••••"
              className={`form-control form-control-lg pe-5 ${errors.password ? 'is-invalid' : ''}`}
              {...register('password', { required: 'Enter your password' })} />
            <button type="button" className="btn btn-link position-absolute end-0 top-50 translate-middle-y text-muted-hh" style={{ zIndex: 5 }}
              onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'}>
              <Icon className={`bi ${show ? 'bi-eye-slash' : 'bi-eye'}`} />
            </button>
          </div>
          {errors.password && <div className="invalid-feedback d-block">{errors.password.message}</div>}
        </div>
        <div className="d-flex justify-content-between align-items-center mb-4 mt-3">
          <span className="small text-muted-hh"><Icon className="bi bi-shield-lock me-1" />Restricted access</span>
          <Link to="/forgot-password" className="small fw-medium">Forgot password?</Link>
        </div>
        <button type="submit" className="btn btn-primary btn-lg w-100 py-3" disabled={isSubmitting}>
          {isSubmitting ? <span className="spinner-border spinner-border-sm me-2" /> : <Icon className="bi bi-box-arrow-in-right me-2" />}Sign in as Super Admin
        </button>
      </form>
      {process.env.NODE_ENV !== 'production' && (
        <div className="hh-demo-box mt-4">
          <div className="fw-semibold mb-2"><Icon className="bi bi-info-circle me-1" />Development accounts</div>
          <div className="d-flex flex-wrap gap-2">
            {DEV_ACCOUNTS.map((d) => (
              <button key={d.email} type="button" onClick={() => {
                // Owners and managers sign in on the standard page; carry the dev credentials over.
                if (d.tenant) { navigate('/login', { state: { prefill: { email: d.email, password: d.password } } }); return; }
                setValue('email', d.email); setValue('password', d.password);
              }}>{d.label}</button>
            ))}
          </div>
          <div className="text-muted-hh mt-2" style={{ fontSize: '.72rem' }}>Click to fill the form, then press Sign in (Owner and Manager open the standard sign-in page).</div>
        </div>
      )}
      <p className="text-center text-muted-hh small mt-4 mb-0">Owner or manager? <Link to="/login">Use the standard sign in</Link></p>
    </AuthShell>
  );
}
