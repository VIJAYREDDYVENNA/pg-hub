import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { errorMessage } from '../../services/apiClient';
import { LoginCityscape } from '../../components/common/Illustrations';
import Icon from '../../components/common/Icon';

/** Shared two-panel layout for login / forgot / reset password. */
export function AuthShell({ children }) {
  return (
    <div className="hh-auth">
      <section className="hh-auth-hero" aria-hidden="true">
        <span className="hh-auth-blob a" /><span className="hh-auth-blob b" />
        <div className="hh-auth-hero-content">
          <div className="hh-auth-brand">
            <span className="hh-brand-logo"><Icon className="bi bi-house-heart-fill" /></span>
            <span><span className="d-block fw-bold fs-4 lh-1">HostelHub</span><span className="small opacity-75">Stay Together, Grow Together</span></span>
          </div>
          <h1>Every hostel, room &amp; bed <span>at a single glance.</span></h1>
          <p>Live occupancy, upcoming vacancies, rent collection and WhatsApp reminders for all your hostels in one warm, simple dashboard.</p>
          <div className="hh-hero-feature"><Icon className="bi bi-building" />Interactive building &amp; floor plans</div>
          <div className="hh-hero-feature"><Icon className="bi bi-calendar2-week" />Vacancy forecast for 7, 15 and 30 days</div>
          <div className="hh-hero-feature"><Icon className="bi bi-shield-check" />Secure, role-based access for your staff</div>
        </div>
        <div className="hh-float hh-glass f1"><span className="ic text-success"><Icon className="bi bi-pie-chart-fill" /></span><span><strong>Live occupancy</strong><small>updated with every admission</small></span></div>
        <div className="hh-float hh-glass f2"><span className="ic" style={{ color: '#FF5A0A' }}><Icon className="bi bi-currency-rupee" /></span><span><strong>Rent &amp; receipts</strong><small>partial, advance &amp; overdue</small></span></div>
        <div className="hh-float hh-glass f3"><span className="ic" style={{ color: '#25D366' }}><Icon className="bi bi-whatsapp" /></span><span><strong>WhatsApp reminders</strong><small>with resident consent</small></span></div>
        <div className="hh-auth-art"><LoginCityscape /></div>
      </section>
      <section className="hh-auth-form"><div className="hh-auth-card">{children}</div></section>
    </div>
  );
}

const DEMO = [
  { label: 'Super Admin', email: 'superadmin@hostelhub.dev', password: 'Admin@12345', superAdmin: true },
  { label: 'Owner', email: 'owner@hostelhub.dev', password: 'Owner@123' },
  { label: 'Manager', email: 'manager@hostelhub.dev', password: 'Manager@123' },
];

export default function LoginPage() {
  const { user, login, expired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState(null);
  const [show, setShow] = useState(false);
  const { register, handleSubmit, setValue, formState: { errors, isSubmitting } } = useForm({ defaultValues: { email: '', password: '', remember: true } });

  // Arriving from the Super Admin page's development shortcuts pre-fills the form.
  useEffect(() => {
    const pre = location.state?.prefill;
    if (pre) { setValue('email', pre.email); setValue('password', pre.password); }
  }, [location.state, setValue]);

  if (user) return <Navigate to={user.roles?.includes('SUPER_ADMIN') ? '/super-admin/dashboard' : (location.state?.from || '/')} replace />;

  const onSubmit = async ({ email, password }) => {
    setError(null);
    try {
      await login(email.trim(), password, 'tenant');
      navigate(location.state?.from || '/', { replace: true });
    } catch (e) {
      setError(errorMessage(e, 'Unable to sign in'));
    }
  };

  return (
    <AuthShell>
      <div className="d-flex align-items-center gap-2 mb-4 d-lg-none">
        <span className="hh-brand-logo" style={{ background: 'linear-gradient(135deg,#FF7A2E,#E84900)' }}><Icon className="bi bi-house-heart-fill" /></span>
        <span><span className="d-block fw-bold fs-5 lh-1">HostelHub</span><span className="small text-muted-hh">Stay Together, Grow Together</span></span>
      </div>
      <span className="hh-auth-chip mb-3"><Icon className="bi bi-stars" />Welcome back</span>
      <h2 className="fw-bold mb-1" style={{ fontSize: '1.65rem' }}>Sign in to your account</h2>
      <p className="text-muted-hh mb-4 small">Manage your hostels, residents and payments.</p>
      {expired && !error && <div className="alert alert-warning py-2 small"><Icon className="bi bi-clock-history me-2" />Your session expired. Please sign in again.</div>}
      {error && <div className="alert alert-danger py-2 small" role="alert"><Icon className="bi bi-exclamation-circle me-2" />{error}</div>}
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="mb-3">
          <label htmlFor="email" className="form-label">Email address</label>
          <div className="input-icon">
            <Icon className="bi bi-envelope" />
            <input id="email" type="email" autoComplete="username" placeholder="you@example.com"
              className={`form-control form-control-lg ${errors.email ? 'is-invalid' : ''}`}
              {...register('email', { required: 'Enter your email', pattern: { value: /\S+@\S+\.\S+/, message: 'Enter a valid email' } })} />
          </div>
          {errors.email && <div className="invalid-feedback d-block">{errors.email.message}</div>}
        </div>
        <div className="mb-2">
          <label htmlFor="password" className="form-label">Password</label>
          <div className="input-icon position-relative">
            <Icon className="bi bi-lock" />
            <input id="password" type={show ? 'text' : 'password'} autoComplete="current-password" placeholder="••••••••"
              className={`form-control form-control-lg pe-5 ${errors.password ? 'is-invalid' : ''}`} {...register('password', { required: 'Enter your password' })} />
            <button type="button" className="btn btn-link position-absolute end-0 top-50 translate-middle-y text-muted-hh" style={{ zIndex: 5 }}
              onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'}>
              <Icon className={`bi ${show ? 'bi-eye-slash' : 'bi-eye'}`} />
            </button>
          </div>
          {errors.password && <div className="invalid-feedback d-block">{errors.password.message}</div>}
        </div>
        <div className="d-flex justify-content-between align-items-center mb-4 mt-3">
          <span className="small text-muted-hh"><Icon className="bi bi-shield-lock me-1" />Secure sign-in</span>
          <Link to="/forgot-password" className="small fw-medium">Forgot password?</Link>
        </div>
        <button type="submit" className="btn btn-primary btn-lg w-100 py-3" disabled={isSubmitting}>
          {isSubmitting ? <span className="spinner-border spinner-border-sm me-2" /> : <Icon className="bi bi-box-arrow-in-right me-2" />}Sign in
        </button>
      </form>
      {process.env.NODE_ENV !== 'production' && (
        <div className="hh-demo-box mt-4">
          <div className="fw-semibold mb-2"><Icon className="bi bi-info-circle me-1" />Development accounts</div>
          <div className="d-flex flex-wrap gap-2">
            {DEMO.map((d) => (
              <button key={d.email} type="button" onClick={() => {
                // Platform administrators sign in on their own page; carry the dev credentials over.
                if (d.superAdmin) { navigate('/super-admin/login', { state: { prefill: { email: d.email, password: d.password } } }); return; }
                setValue('email', d.email); setValue('password', d.password);
              }}>
                {d.label}
              </button>
            ))}
          </div>
          <div className="text-muted-hh mt-2" style={{ fontSize: '.72rem' }}>Click to fill the form, then press Sign in (Super Admin opens its own sign-in page).</div>
        </div>
      )}
      <p className="text-center text-muted-hh small mt-4 mb-0">© {new Date().getFullYear()} HostelHub</p>
    </AuthShell>
  );
}
